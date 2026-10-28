package com.briefwise.jobs.infrastructure.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.jobs.JobsFacade;
import com.briefwise.jobs.JobsFacade.JobRunView;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.UnknownJobException;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.Execution;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.ExecutionMode;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.JobProperties;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.Llm;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.LlmProvider;
import com.briefwise.shared.infrastructure.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class JobLauncherTest {

    private final JobsFacade jobs = mock(JobsFacade.class);
    private final JobLauncher launcher = new JobLauncher(jobs, new BriefwiseProperties(
            new Execution(ExecutionMode.ON_DEMAND),
            Map.of("news-ingest", new JobProperties(true, null), "news-classify", new JobProperties(false, null)),
            new Llm(LlmProvider.REPLAY, null), null));

    @Test
    void enabled_job_runs_with_its_own_correlation_id() {
        AtomicReference<String> correlationId = new AtomicReference<>();
        Instant t = Instant.parse("2026-10-28T04:30:00Z");
        when(jobs.run("news-ingest", JobTrigger.RUNNER)).thenAnswer(call -> {
            correlationId.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return new JobRunView("news-ingest", JobTrigger.RUNNER, JobStatus.SUCCEEDED, t, t, List.of());
        });

        assertThat(launcher.runIfEnabled("news-ingest", JobTrigger.RUNNER)).isPresent();
        assertThat(correlationId.get()).startsWith("news-ingest-");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void disabled_job_is_not_run() {
        assertThat(launcher.runIfEnabled("news-classify", JobTrigger.SCHEDULER)).isEmpty();
        verify(jobs, never()).run(anyString(), any());
    }

    @Test
    void unknown_job_is_an_error_not_disabled() {
        assertThatThrownBy(() -> launcher.runIfEnabled("risk-alerts", JobTrigger.RUNNER))
                .isInstanceOf(UnknownJobException.class);
    }
}
