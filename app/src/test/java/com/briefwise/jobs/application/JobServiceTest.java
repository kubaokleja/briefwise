package com.briefwise.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.jobs.domain.model.EvalResult;
import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobRun;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobStep;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.PublishResult;
import com.briefwise.jobs.domain.model.RunPolicy;
import com.briefwise.jobs.domain.port.JobLock;
import com.briefwise.jobs.domain.port.NewsPipeline;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class JobServiceTest {

    private static final Duration MAX = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-28T04:30:00Z"));
    private final NewsPipeline news = mock(NewsPipeline.class);
    private final FakeLock lock = new FakeLock();
    private final JobService service = new JobService(news, lock, new RunPolicy(MAX, Duration.ZERO), clock);

    @BeforeEach
    void happyPipeline() {
        when(news.ingest()).thenReturn(Map.of("inserted", 2));
        when(news.publishPending()).thenReturn(new PublishResult(2, 0, false));
        when(news.consumerLag()).thenReturn(0L);
        when(news.classifyPending(any())).thenReturn(Map.of("picked", 0));
        when(news.summarizePortfolios(any())).thenReturn(Map.of("generated", 3, "stoppedOnDeferral", false));
    }

    private static JobStep step(JobRun run, String name) {
        return run.steps().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void runner_ingests_publishes_waits_for_the_consumers_sweeps_then_summarizes() {
        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(run.steps()).extracting(JobStep::name)
                .containsExactly("ingest", "publish", "drain", "sweep", "summarize");
        assertThat(step(run, "ingest").result()).containsEntry("inserted", 2);
        assertThat(step(run, "summarize").result()).containsEntry("generated", 3);
        InOrder order = inOrder(news);
        order.verify(news).ingest();
        order.verify(news).publishPending();
        order.verify(news).consumerLag();
        order.verify(news).classifyPending(any());
        order.verify(news).summarizePortfolios(any());
    }

    @Test
    void admin_run_does_not_wait_for_the_consumers_and_leaves_the_summaries_to_the_scheduled_run() {
        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.ADMIN);

        assertThat(run.steps()).extracting(JobStep::name).containsExactly("ingest", "publish", "sweep", "summarize");
        assertThat(step(run, "summarize").status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(step(run, "summarize").message().orElseThrow()).contains("scheduled run");
        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
        verify(news, never()).consumerLag();
        verify(news, never()).summarizePortfolios(any());
    }

    @Test
    void classify_job_is_the_sweep_alone() {
        JobRun run = service.run(JobName.NEWS_CLASSIFY, JobTrigger.RUNNER);

        assertThat(run.steps()).extracting(JobStep::name).containsExactly("sweep");
        verify(news, never()).ingest();
        verify(news, never()).summarizePortfolios(any());
    }

    @Test
    void deferred_summaries_skip_the_step_without_failing_the_run() {
        when(news.summarizePortfolios(any())).thenReturn(Map.of("deferred", 1, "stoppedOnDeferral", true));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(step(run, "summarize").status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(step(run, "summarize").result()).containsEntry("deferred", 1);
    }

    @Test
    void failing_summaries_fail_the_run() {
        when(news.summarizePortfolios(any())).thenThrow(new IllegalStateException("database down"));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.FAILED);
        assertThat(step(run, "summarize").status()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void publish_repeats_until_nothing_is_pending() {
        when(news.publishPending()).thenReturn(new PublishResult(100, 50, false), new PublishResult(50, 0, false));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.ADMIN);

        assertThat(step(run, "publish").result()).containsEntry("published", 150).containsEntry("pending", 0);
        verify(news, times(2)).publishPending();
    }

    @Test
    void broker_down_fails_the_publish_but_the_sweep_still_runs() {
        when(news.publishPending()).thenReturn(new PublishResult(0, 7, true));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.FAILED);
        assertThat(step(run, "publish").status()).isEqualTo(JobStatus.FAILED);
        assertThat(step(run, "drain").status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(step(run, "sweep").status()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void lag_that_never_reaches_zero_times_out_and_skips_the_sweep() {
        when(news.consumerLag()).thenAnswer(call -> {
            clock.advance(Duration.ofMinutes(4));
            return 3L;
        });

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(step(run, "drain").result()).containsEntry("lag", 3L);
        assertThat(step(run, "sweep").status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(step(run, "summarize").status()).isEqualTo(JobStatus.SKIPPED);
        verify(news, never()).classifyPending(any());
        verify(news, never()).summarizePortfolios(any());
    }

    @Test
    void lag_is_polled_until_zero() {
        when(news.consumerLag()).thenReturn(5L, 1L, 0L);

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(step(run, "drain").status()).isEqualTo(JobStatus.SUCCEEDED);
        verify(news, times(3)).consumerLag();
    }

    @Test
    void failing_ingest_is_recorded_and_the_run_goes_on() {
        when(news.ingest()).thenThrow(new IllegalStateException("Finnhub rejected the API key"));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.FAILED);
        assertThat(step(run, "ingest").message()).hasValueSatisfying(m -> assertThat(m).contains("API key"));
        verify(news).classifyPending(any());
    }

    @Test
    void used_up_budget_skips_the_step_and_is_not_a_failure() {
        when(news.classifyPending(any())).thenThrow(new UsageLimitExceededException("news-classification",
                new BigDecimal("0.10"), new BigDecimal("0.10")));

        JobRun run = service.run(JobName.NEWS_CLASSIFY, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SKIPPED);
    }

    @Test
    void sweep_and_summaries_get_the_runs_deadline() {
        service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        Instant deadline = Instant.parse("2026-10-28T04:45:00Z");
        verify(news).classifyPending(eq(deadline));
        verify(news).summarizePortfolios(eq(deadline));
    }

    @Test
    void a_sweep_where_at_least_half_the_picked_items_failed_fails_the_run() {
        when(news.classifyPending(any())).thenReturn(Map.of("picked", 4, "failed", 2, "stoppedOnDeferral", false));

        JobRun run = service.run(JobName.NEWS_CLASSIFY, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.FAILED);
        assertThat(step(run, "sweep").message().orElseThrow()).contains("2 of 4 swept items failed");
    }

    @Test
    void an_isolated_sweep_failure_stays_green_and_is_retried_next_run() {
        when(news.classifyPending(any())).thenReturn(Map.of("picked", 5, "failed", 2, "classified", 3));

        JobRun run = service.run(JobName.NEWS_CLASSIFY, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(step(run, "sweep").result()).containsEntry("failed", 2);
    }

    @Test
    void a_sweep_stopped_on_a_budget_deferral_is_skipped_like_the_summaries() {
        when(news.classifyPending(any())).thenReturn(Map.of("picked", 3, "pending", 1, "stoppedOnDeferral", true));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(step(run, "sweep").status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void a_sweep_stopped_at_the_deadline_times_out_and_skips_the_summaries() {
        when(news.classifyPending(any())).thenReturn(Map.of("picked", 120, "stoppedOnDeadline", true));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(step(run, "sweep").status()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(step(run, "summarize").status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(run.status()).isEqualTo(JobStatus.TIMED_OUT);
        verify(news, never()).summarizePortfolios(any());
    }

    @Test
    void summaries_stopped_at_the_deadline_time_out() {
        when(news.summarizePortfolios(any())).thenReturn(Map.of("generated", 1, "stoppedOnDeadline", true));

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(step(run, "summarize").status()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(run.status()).isEqualTo(JobStatus.TIMED_OUT);
    }

    @Test
    void a_second_run_while_one_is_going_is_skipped_without_touching_the_pipeline() {
        lock.taken = true;

        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.ADMIN);

        assertThat(run.status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(run.steps()).extracting(JobStep::name).containsExactly("lock");
        assertThat(step(run, "lock").message().orElseThrow()).contains("still going");
        verify(news, never()).ingest();
    }

    @Test
    void the_lock_is_released_after_the_run_even_when_a_step_fails() {
        when(news.ingest()).thenThrow(new IllegalStateException("boom"));

        service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(lock.acquired).isEqualTo(1);
        assertThat(lock.released).isEqualTo(1);
    }

    @Test
    void a_passing_eval_succeeds_and_reports_its_figures() {
        when(news.evaluateClassification()).thenReturn(new EvalResult(true, "no baseline yet",
                Map.of("status", "PASSED", "sentimentMacroF1", 0.71)));

        JobRun run = service.run(JobName.NEWS_CLASSIFICATION_EVAL, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(run.steps()).extracting(JobStep::name).containsExactly("evaluate");
        assertThat(step(run, "evaluate").result()).containsEntry("sentimentMacroF1", 0.71);
        verify(news, never()).ingest();
    }

    @Test
    void a_regressed_refused_or_unfinished_eval_fails_the_run() {
        when(news.evaluateClassification()).thenReturn(new EvalResult(false, "budget used up",
                Map.of("status", "REFUSED")));

        JobRun run = service.run(JobName.NEWS_CLASSIFICATION_EVAL, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.FAILED);
        assertThat(step(run, "evaluate").message()).contains("budget used up");
    }

    @Test
    void a_missing_golden_set_fails_the_run() {
        when(news.evaluateClassification()).thenThrow(new IllegalStateException("No golden set"));

        JobRun run = service.run(JobName.NEWS_CLASSIFICATION_EVAL, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.FAILED);
    }

    /** One lock for every job: {@code taken} simulates another run holding it. */
    private static final class FakeLock implements JobLock {

        boolean taken;
        int acquired;
        int released;

        @Override
        public Optional<Held> tryAcquire(JobName job) {
            if (taken) {
                return Optional.empty();
            }
            acquired++;
            return Optional.of(() -> released++);
        }
    }

    /** A clock the test moves forward, so a timeout needs no real waiting. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
