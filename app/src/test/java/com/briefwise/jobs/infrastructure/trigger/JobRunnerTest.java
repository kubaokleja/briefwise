package com.briefwise.jobs.infrastructure.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.briefwise.jobs.JobsFacade.JobRunView;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.UnknownJobException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.DefaultApplicationArguments;

/** The exit code is the contract with the GitHub Actions workflow (ADR 0016). */
class JobRunnerTest {

    private final JobLauncher launcher = mock(JobLauncher.class);

    private int exitCodeFor(String jobId) {
        JobRunner runner = new JobRunner(launcher, jobId);
        runner.run(new DefaultApplicationArguments());
        return runner.getExitCode();
    }

    private static JobRunView run(JobStatus status) {
        Instant t = Instant.parse("2026-10-28T04:30:00Z");
        return new JobRunView("news-ingest", JobTrigger.RUNNER, status, t, t, List.of());
    }

    @ParameterizedTest
    @CsvSource({"SUCCEEDED, 0", "SKIPPED, 0", "FAILED, 1", "TIMED_OUT, 1"})
    void run_status_maps_to_the_exit_code(JobStatus status, int exitCode) {
        when(launcher.runIfEnabled("news-ingest", JobTrigger.RUNNER)).thenReturn(Optional.of(run(status)));

        assertThat(exitCodeFor("news-ingest")).isEqualTo(exitCode);
    }

    @Test
    void disabled_job_exits_0() {
        when(launcher.runIfEnabled("news-ingest", JobTrigger.RUNNER)).thenReturn(Optional.empty());

        assertThat(exitCodeFor("news-ingest")).isZero();
    }

    @Test
    void unknown_or_malformed_job_exits_2() {
        when(launcher.runIfEnabled("risk-alerts", JobTrigger.RUNNER)).thenThrow(new UnknownJobException("risk-alerts"));
        when(launcher.runIfEnabled("Bad Id", JobTrigger.RUNNER)).thenThrow(new IllegalArgumentException("bad"));

        assertThat(exitCodeFor("risk-alerts")).isEqualTo(2);
        assertThat(exitCodeFor("Bad Id")).isEqualTo(2);
    }

    @Test
    void unexpected_error_exits_1() {
        when(launcher.runIfEnabled("news-ingest", JobTrigger.RUNNER)).thenThrow(new IllegalStateException("boom"));

        assertThat(exitCodeFor("news-ingest")).isEqualTo(1);
    }
}
