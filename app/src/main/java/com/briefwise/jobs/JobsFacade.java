package com.briefwise.jobs;

import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobRun;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobStep;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.UnknownJobException;
import com.briefwise.jobs.domain.port.Jobs;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code jobs}. The jobs' own inbound adapters (admin endpoint,
 * scheduler, job runner) go through it too. No logic here, delegate only.
 */
@Component
public class JobsFacade {

    private final Jobs jobs;

    JobsFacade(Jobs jobs) {
        this.jobs = jobs;
    }

    /**
     * Runs a job to the end and reports each step. A failing step does not throw; it is reported as {@code FAILED}.
     *
     * @throws IllegalArgumentException if {@code jobId} is not a well-formed job id
     * @throws UnknownJobException if no job has this id
     */
    public JobRunView run(String jobId, JobTrigger trigger) {
        JobRun run = jobs.run(JobName.fromId(jobId), trigger);
        return new JobRunView(run.job().id(), run.trigger(), run.status(), run.startedAt(), run.endedAt(),
                run.steps().stream().map(JobsFacade::view).toList());
    }

    private static JobStepView view(JobStep step) {
        return new JobStepView(step.name(), step.status(), step.result(), step.message().orElse(""));
    }

    public record JobRunView(String job, JobTrigger trigger, JobStatus status, Instant startedAt, Instant endedAt,
                             List<JobStepView> steps) {}

    /** {@code message} is empty when the step succeeded. */
    public record JobStepView(String name, JobStatus status, Map<String, Object> result, String message) {}
}
