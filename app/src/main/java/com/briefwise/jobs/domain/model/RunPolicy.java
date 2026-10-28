package com.briefwise.jobs.domain.model;

import java.time.Duration;

/** {@code maxDuration}: a run stops waiting after this long. {@code lagPollInterval}: pause between lag checks. */
public record RunPolicy(Duration maxDuration, Duration lagPollInterval) {

    public RunPolicy {
        if (maxDuration == null || maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration must be positive, was " + maxDuration);
        }
        if (lagPollInterval == null || lagPollInterval.isNegative()) {
            throw new IllegalArgumentException("lagPollInterval must be zero or positive, was " + lagPollInterval);
        }
    }
}
