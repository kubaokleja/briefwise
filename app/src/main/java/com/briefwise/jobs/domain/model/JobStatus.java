package com.briefwise.jobs.domain.model;

/**
 * Outcome of a job step or a whole run. {@code SKIPPED}: nothing to do or not allowed now (e.g. the LLM budget is
 * used up, ADR 0008); not an error. {@code TIMED_OUT}: {@code max-duration} was reached; the leftovers are picked up
 * by the next run. The job runner exits 1 on {@code FAILED} and {@code TIMED_OUT} (ADR 0016).
 */
public enum JobStatus {
    SUCCEEDED,
    SKIPPED,
    FAILED,
    TIMED_OUT;

    public boolean isFailure() {
        return this == FAILED || this == TIMED_OUT;
    }
}
