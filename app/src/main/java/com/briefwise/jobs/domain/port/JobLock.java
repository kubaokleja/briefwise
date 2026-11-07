package com.briefwise.jobs.domain.port;

import com.briefwise.jobs.domain.model.JobName;
import java.util.Optional;

/**
 * Outbound port: at most one run of a job at a time, across processes (admin trigger, scheduler, job runner). GitHub
 * serialises only its own workflow runs; this covers the rest.
 */
public interface JobLock {

    /** The lock of the job's {@link JobName#lockGroup()}, or empty if another run holds it. Never waits. */
    Optional<Held> tryAcquire(JobName job);

    /** A held lock; {@link #close()} releases it and never throws. */
    interface Held extends AutoCloseable {

        @Override
        void close();
    }
}
