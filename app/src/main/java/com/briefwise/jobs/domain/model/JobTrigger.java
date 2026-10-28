package com.briefwise.jobs.domain.model;

/**
 * Who started a run (DESIGN 13.2). Only the {@code RUNNER} waits until Kafka consumer lag is 0 before it sweeps:
 * the process exits afterwards, so the work must be done. In a running app the listener consumes on its own.
 */
public enum JobTrigger {
    ADMIN,
    SCHEDULER,
    RUNNER;

    public boolean waitsForConsumers() {
        return this == RUNNER;
    }
}
