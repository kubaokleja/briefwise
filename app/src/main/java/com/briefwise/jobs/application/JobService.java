package com.briefwise.jobs.application;

import com.briefwise.jobs.domain.model.EvalResult;
import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobRun;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobStep;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.PublishResult;
import com.briefwise.jobs.domain.model.RunPolicy;
import com.briefwise.jobs.domain.port.JobLock;
import com.briefwise.jobs.domain.port.Jobs;
import com.briefwise.jobs.domain.port.NewsPipeline;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a job as a sequence of steps (DESIGN 7.1, ADR 0016). A failing step is recorded and the run goes on where the
 * next step still makes sense: the sweep only touches items older than its {@code min-age}, so it is safe even after
 * a failed ingest or publish. Only {@code max-duration} stops a run early: publish, drain, the sweep and the
 * summaries check it (the sweep and summaries between items); ingest has its own HTTP timeouts. The leftovers stay in
 * Postgres for the next run (outbox rows pending, items {@code RECEIVED}, previous summaries).
 * <p>
 * One run per lock group at a time ({@link JobLock}; {@code news-ingest} and {@code news-classify} share one): a second
 * run while one is going returns at once as {@code SKIPPED}.
 * <p>
 * {@code news-ingest}: ingest → publish until no outbox row is pending → (runner only) wait until the consumer lag
 * is 0 → sweep → summarize (portfolio summaries, ADR 0017; not on an admin run, which does not wait for the
 * consumers). {@code news-classify}: sweep.
 * {@code news-classification-eval}: evaluate (ADR 0018); the step fails unless the eval passed its gate, so the job
 * runner exits 1 and the {@code evals.yml} check goes red.
 */
@Service
class JobService implements Jobs {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final NewsPipeline news;
    private final JobLock locks;
    private final RunPolicy policy;
    private final Clock clock;

    JobService(NewsPipeline news, JobLock locks, RunPolicy policy, Clock clock) {
        this.news = news;
        this.locks = locks;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public JobRun run(JobName job, JobTrigger trigger) {
        Instant startedAt = clock.instant();
        Optional<JobLock.Held> lock = locks.tryAcquire(job);
        if (lock.isEmpty()) {
            log.info("Job {} not started ({}): another {} run is still going", job.id(), trigger, job.lockGroup());
            return new JobRun(job, trigger, List.of(JobStep.of("lock", JobStatus.SKIPPED,
                    "another " + job.lockGroup() + " run is still going")), startedAt, clock.instant());
        }
        try (JobLock.Held held = lock.get()) {
            return runLocked(job, trigger, startedAt);
        }
    }

    private JobRun runLocked(JobName job, JobTrigger trigger, Instant startedAt) {
        Instant deadline = startedAt.plus(policy.maxDuration());
        log.info("Job {} started ({})", job.id(), trigger);
        List<JobStep> steps = switch (job) {
            case NEWS_INGEST -> newsIngest(trigger, deadline);
            case NEWS_CLASSIFY -> List.of(sweep(deadline));
            case NEWS_CLASSIFICATION_EVAL -> List.of(evaluate());
        };
        JobRun run = new JobRun(job, trigger, steps, startedAt, clock.instant());
        log.info("Job {} {} in {} ms: {}", job.id(), run.status(),
                Duration.between(run.startedAt(), run.endedAt()).toMillis(), steps);
        return run;
    }

    private List<JobStep> newsIngest(JobTrigger trigger, Instant deadline) {
        List<JobStep> steps = new ArrayList<>();
        steps.add(step("ingest", news::ingest));
        JobStep publish = publish(deadline);
        steps.add(publish);
        if (trigger.waitsForConsumers()) {
            steps.add(publish.status() == JobStatus.FAILED
                    ? JobStep.of("drain", JobStatus.SKIPPED, "publish failed; nothing new to wait for")
                    : drain(deadline));
        }
        steps.add(timedOut(steps)
                ? JobStep.of("sweep", JobStatus.SKIPPED, "max-duration reached; the next run sweeps")
                : sweep(deadline));
        if (trigger == JobTrigger.ADMIN) {
            // No drain on an admin run: the consumer is still classifying what was just ingested, so a summary now
            // would miss it, and the gate reopens today's summary only for a new HIGH item.
            steps.add(JobStep.of("summarize", JobStatus.SKIPPED,
                    "admin run: summaries are made by the scheduled run, after the consumers caught up"));
        } else {
            steps.add(timedOut(steps)
                    ? JobStep.of("summarize", JobStatus.SKIPPED, "max-duration reached; previous summaries stay")
                    : summarize(deadline));
        }
        return steps;
    }

    private static boolean timedOut(List<JobStep> steps) {
        return steps.stream().anyMatch(s -> s.status() == JobStatus.TIMED_OUT);
    }

    /**
     * The sweep (ADR 0016). Per-item exceptions are counted, not thrown, so the step decides: {@code FAILED} when at
     * least half of the picked items failed (a systematic fault: the runner exits 1 and GitHub mails); isolated
     * failures stay in the log and are retried. {@code TIMED_OUT} when it stopped at the deadline, {@code SKIPPED}
     * when it stopped at a deferral (budget used up), like the summaries.
     */
    private JobStep sweep(Instant deadline) {
        JobStep step = step("sweep", () -> news.classifyPending(deadline));
        if (step.status() != JobStatus.SUCCEEDED) {
            return step;
        }
        int picked = count(step, "picked");
        int failed = count(step, "failed");
        if (failed > 0 && 2 * failed >= picked) {
            return new JobStep("sweep", JobStatus.FAILED, step.result(),
                    Optional.of(failed + " of " + picked + " swept items failed; see the log"));
        }
        if (flag(step, "stoppedOnDeadline")) {
            return new JobStep("sweep", JobStatus.TIMED_OUT, step.result(),
                    Optional.of("max-duration reached; the rest stays RECEIVED for the next run"));
        }
        if (flag(step, "stoppedOnDeferral")) {
            return new JobStep("sweep", JobStatus.SKIPPED, step.result(),
                    Optional.of("classification deferred (budget used up); the rest waits for the next run"));
        }
        return step;
    }

    /**
     * Daily portfolio summaries from what is classified now (ADR 0017). A deferral (budget used up, no chat model)
     * stops the step and marks it {@code SKIPPED}, like a budget stop elsewhere; the deadline marks it
     * {@code TIMED_OUT}. Either way the remaining portfolios keep their previous summary.
     */
    private JobStep summarize(Instant deadline) {
        JobStep step = step("summarize", () -> news.summarizePortfolios(deadline));
        if (step.status() != JobStatus.SUCCEEDED) {
            return step;
        }
        if (flag(step, "stoppedOnDeadline")) {
            return new JobStep("summarize", JobStatus.TIMED_OUT, step.result(),
                    Optional.of("max-duration reached; the remaining portfolios keep their previous summary"));
        }
        if (flag(step, "stoppedOnDeferral")) {
            return new JobStep("summarize", JobStatus.SKIPPED, step.result(),
                    Optional.of("summary deferred (budget used up or no model); previous summaries stay"));
        }
        return step;
    }

    private static boolean flag(JobStep step, String key) {
        return Boolean.TRUE.equals(step.result().get(key));
    }

    private static int count(JobStep step, String key) {
        return step.result().get(key) instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Only a pass succeeds: a regression, a refused batch (budget, leakage) and an unfinished batch all fail, because
     * an eval that did not measure must not look like a green check.
     */
    private JobStep evaluate() {
        try {
            EvalResult result = news.evaluateClassification();
            return new JobStep("evaluate", result.passed() ? JobStatus.SUCCEEDED : JobStatus.FAILED,
                    result.figures(), Optional.of(result.message()));
        } catch (RuntimeException e) {
            return failed("evaluate", e);
        }
    }

    /**
     * The relay sends one batch per call, so it is called until nothing is pending. It stops when the broker is
     * unavailable (rows stay pending), when another relay holds the remaining rows (nothing sent) or at the deadline.
     */
    private JobStep publish(Instant deadline) {
        int published = 0;
        try {
            while (true) {
                PublishResult result = news.publishPending();
                published += result.published();
                Map<String, Object> counts = Map.of("published", published, "pending", result.pending());
                if (result.brokerUnavailable()) {
                    return new JobStep("publish", JobStatus.FAILED, counts,
                            Optional.of("Kafka unavailable; rows stay pending for the next run"));
                }
                if (result.pending() == 0 || result.published() == 0) {
                    return JobStep.succeeded("publish", counts);
                }
                if (!clock.instant().isBefore(deadline)) {
                    return new JobStep("publish", JobStatus.TIMED_OUT, counts,
                            Optional.of("max-duration reached with rows pending"));
                }
            }
        } catch (RuntimeException e) {
            return failed("publish", e);
        }
    }

    /** Waits until every published message was consumed (or sent to the DLT), polling the consumer lag. */
    private JobStep drain(Instant deadline) {
        Instant start = clock.instant();
        try {
            while (true) {
                long lag = news.consumerLag();
                if (lag == 0) {
                    return JobStep.succeeded("drain",
                            Map.of("waitedMs", Duration.between(start, clock.instant()).toMillis()));
                }
                if (!clock.instant().isBefore(deadline)) {
                    return new JobStep("drain", JobStatus.TIMED_OUT, Map.of("lag", lag),
                            Optional.of("consumer lag still " + lag + " at max-duration"));
                }
                Thread.sleep(policy.lagPollInterval());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return JobStep.of("drain", JobStatus.FAILED, "interrupted");
        } catch (RuntimeException e) {
            return failed("drain", e);
        }
    }

    private static JobStep step(String name, Supplier<Map<String, Object>> action) {
        try {
            return JobStep.succeeded(name, action.get());
        } catch (UsageLimitExceededException e) {
            log.info("Job step {} skipped: {}", name, e.getMessage());
            return JobStep.of(name, JobStatus.SKIPPED, e.getMessage());
        } catch (RuntimeException e) {
            return failed(name, e);
        }
    }

    private static JobStep failed(String name, RuntimeException e) {
        log.warn("Job step {} failed", name, e);
        return JobStep.of(name, JobStatus.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}
