package com.briefwise.jobs.domain.port;

import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobRun;
import com.briefwise.jobs.domain.model.JobTrigger;

/** Inbound port: run one job to the end. Implemented by a package-private service. */
public interface Jobs {

    /** Never throws for a failing step; the step is recorded as {@code FAILED} and the run goes on. */
    JobRun run(JobName job, JobTrigger trigger);
}
