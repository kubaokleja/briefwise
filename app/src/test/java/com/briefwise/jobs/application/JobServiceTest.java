package com.briefwise.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobRun;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobStep;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.PublishResult;
import com.briefwise.jobs.domain.model.RunPolicy;
import com.briefwise.jobs.domain.port.NewsPipeline;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class JobServiceTest {

    private static final Duration MAX = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-28T04:30:00Z"));
    private final NewsPipeline news = mock(NewsPipeline.class);
    private final JobService service = new JobService(news, new RunPolicy(MAX, Duration.ZERO), clock);

    @BeforeEach
    void happyPipeline() {
        when(news.ingest()).thenReturn(Map.of("inserted", 2));
        when(news.publishPending()).thenReturn(new PublishResult(2, 0, false));
        when(news.consumerLag()).thenReturn(0L);
        when(news.classifyPending()).thenReturn(Map.of("picked", 0));
    }

    private static JobStep step(JobRun run, String name) {
        return run.steps().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void runner_ingests_publishes_waits_for_the_consumers_then_sweeps() {
        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(run.steps()).extracting(JobStep::name).containsExactly("ingest", "publish", "drain", "sweep");
        assertThat(step(run, "ingest").result()).containsEntry("inserted", 2);
        InOrder order = inOrder(news);
        order.verify(news).ingest();
        order.verify(news).publishPending();
        order.verify(news).consumerLag();
        order.verify(news).classifyPending();
    }

    @Test
    void admin_run_does_not_wait_for_the_consumers() {
        JobRun run = service.run(JobName.NEWS_INGEST, JobTrigger.ADMIN);

        assertThat(run.steps()).extracting(JobStep::name).containsExactly("ingest", "publish", "sweep");
        verify(news, never()).consumerLag();
    }

    @Test
    void classify_job_is_the_sweep_alone() {
        JobRun run = service.run(JobName.NEWS_CLASSIFY, JobTrigger.RUNNER);

        assertThat(run.steps()).extracting(JobStep::name).containsExactly("sweep");
        verify(news, never()).ingest();
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
        verify(news, never()).classifyPending();
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
        verify(news).classifyPending();
    }

    @Test
    void used_up_budget_skips_the_step_and_is_not_a_failure() {
        when(news.classifyPending()).thenThrow(new UsageLimitExceededException("news-classification",
                new BigDecimal("0.10"), new BigDecimal("0.10")));

        JobRun run = service.run(JobName.NEWS_CLASSIFY, JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SKIPPED);
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
