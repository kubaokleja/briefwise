package com.briefwise.jobs.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * One run of a job. The run status follows from its steps: {@code FAILED} if a step failed, else {@code TIMED_OUT}
 * if one timed out, else {@code SKIPPED} if every step was skipped, else {@code SUCCEEDED}.
 */
public record JobRun(JobName job, JobTrigger trigger, List<JobStep> steps, Instant startedAt, Instant endedAt) {

    public JobRun {
        steps = List.copyOf(steps);
    }

    public JobStatus status() {
        if (steps.stream().anyMatch(step -> step.status() == JobStatus.FAILED)) {
            return JobStatus.FAILED;
        }
        if (steps.stream().anyMatch(step -> step.status() == JobStatus.TIMED_OUT)) {
            return JobStatus.TIMED_OUT;
        }
        if (!steps.isEmpty() && steps.stream().allMatch(step -> step.status() == JobStatus.SKIPPED)) {
            return JobStatus.SKIPPED;
        }
        return JobStatus.SUCCEEDED;
    }
}
