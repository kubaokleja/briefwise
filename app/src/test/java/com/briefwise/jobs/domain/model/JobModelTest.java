package com.briefwise.jobs.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Job names, run status and run policy. */
class JobModelTest {

    private static final Instant T0 = Instant.parse("2026-10-28T04:30:00Z");

    @Test
    void job_ids_resolve_to_names() {
        assertThat(JobName.fromId("news-ingest")).isEqualTo(JobName.NEWS_INGEST);
        assertThat(JobName.fromId("news-classify")).isEqualTo(JobName.NEWS_CLASSIFY);
    }

    @Test
    void well_formed_but_unknown_id_is_not_found() {
        assertThatThrownBy(() -> JobName.fromId("risk-alerts")).isInstanceOf(UnknownJobException.class)
                .hasMessageContaining("risk-alerts");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "News-Ingest", "news ingest", "-news", "news_ingest"})
    void malformed_id_is_rejected(String id) {
        assertThatThrownBy(() -> JobName.fromId(id)).isInstanceOf(IllegalArgumentException.class);
    }

    private static JobRun run(JobStatus... statuses) {
        List<JobStep> steps = java.util.Arrays.stream(statuses)
                .map(s -> s == JobStatus.SUCCEEDED ? JobStep.succeeded("step", Map.of()) : JobStep.of("step", s, "x"))
                .toList();
        return new JobRun(JobName.NEWS_INGEST, JobTrigger.RUNNER, steps, T0, T0);
    }

    @Test
    void run_status_follows_from_its_steps() {
        assertThat(run(JobStatus.SUCCEEDED, JobStatus.SKIPPED).status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(run(JobStatus.SKIPPED, JobStatus.SKIPPED).status()).isEqualTo(JobStatus.SKIPPED);
        assertThat(run(JobStatus.TIMED_OUT, JobStatus.SKIPPED).status()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(run(JobStatus.TIMED_OUT, JobStatus.FAILED).status()).isEqualTo(JobStatus.FAILED);
        assertThat(JobStatus.TIMED_OUT.isFailure()).isTrue();
        assertThat(JobStatus.SKIPPED.isFailure()).isFalse();
    }

    @Test
    void run_policy_needs_a_positive_max_duration() {
        assertThatThrownBy(() -> new RunPolicy(Duration.ZERO, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunPolicy(Duration.ofMinutes(15), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RunPolicy(Duration.ofMinutes(15), Duration.ZERO).lagPollInterval()).isZero();
    }
}
