package com.briefwise.jobs.application;

import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobRun;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobStep;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.PublishResult;
import com.briefwise.jobs.domain.model.RunPolicy;
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
 * a failed ingest or publish. Only {@code max-duration} stops a run early; the leftovers stay in Postgres for the
 * next run (outbox rows pending, items {@code RECEIVED}).
 * <p>
 * {@code news-ingest}: ingest → publish until no outbox row is pending → (runner only) wait until the consumer lag
 * is 0 → sweep. {@code news-classify}: sweep.
 */
@Service
class JobService implements Jobs {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final NewsPipeline news;
    private final RunPolicy policy;
    private final Clock clock;

    JobService(NewsPipeline news, RunPolicy policy, Clock clock) {
        this.news = news;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public JobRun run(JobName job, JobTrigger trigger) {
        Instant startedAt = clock.instant();
        Instant deadline = startedAt.plus(policy.maxDuration());
        log.info("Job {} started ({})", job.id(), trigger);
        List<JobStep> steps = switch (job) {
            case NEWS_INGEST -> newsIngest(trigger, deadline);
            case NEWS_CLASSIFY -> List.of(step("sweep", news::classifyPending));
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
        boolean timedOut = steps.stream().anyMatch(s -> s.status() == JobStatus.TIMED_OUT);
        steps.add(timedOut
                ? JobStep.of("sweep", JobStatus.SKIPPED, "max-duration reached; the next run sweeps")
                : step("sweep", news::classifyPending));
        return steps;
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
