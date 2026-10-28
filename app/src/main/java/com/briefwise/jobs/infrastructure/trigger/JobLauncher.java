package com.briefwise.jobs.infrastructure.trigger;

import com.briefwise.jobs.JobsFacade;
import com.briefwise.jobs.JobsFacade.JobRunView;
import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.JobProperties;
import com.briefwise.shared.infrastructure.web.CorrelationIdFilter;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Shared by the scheduler and the job runner: honours {@code briefwise.jobs.<id>.enabled} (ADR 0002) and gives the
 * run its own correlation ID in the MDC, as the request filter does for the admin endpoint. The admin endpoint does
 * not use it: a disabled job (e.g. {@code brief}) still runs on demand (DESIGN 7.4).
 */
@Component
class JobLauncher {

    private static final Logger log = LoggerFactory.getLogger(JobLauncher.class);

    private final JobsFacade jobs;
    private final BriefwiseProperties properties;

    JobLauncher(JobsFacade jobs, BriefwiseProperties properties) {
        this.jobs = jobs;
        this.properties = properties;
    }

    /**
     * @return the run, or empty if the job is disabled
     * @throws IllegalArgumentException if {@code jobId} is not a well-formed job id
     * @throws com.briefwise.jobs.domain.model.UnknownJobException if no job has this id
     */
    Optional<JobRunView> runIfEnabled(String jobId, JobTrigger trigger) {
        JobName.fromId(jobId);   // an unknown job is an error, not "disabled"
        JobProperties job = properties.jobs().get(jobId);
        if (job == null || !job.enabled()) {
            log.info("Job {} is disabled (briefwise.jobs.{}.enabled); not run by the {}", jobId, jobId, trigger);
            return Optional.empty();
        }
        MDC.put(CorrelationIdFilter.MDC_KEY, jobId + "-" + UUID.randomUUID());
        try {
            return Optional.of(jobs.run(jobId, trigger));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
