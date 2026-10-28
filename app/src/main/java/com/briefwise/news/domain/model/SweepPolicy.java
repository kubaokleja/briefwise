package com.briefwise.news.domain.model;

import java.time.Duration;

/**
 * Which stuck items the sweep picks up (ADR 0016). {@code minAge}: only items created at least this long ago, so the
 * sweep never races the relay or a consumer that is still working. {@code batchSize}: items per run, oldest first.
 * {@code maxAttempts}: sweep attempts per item before it gives up as {@code UNCLASSIFIED} ({@code RETRIES_EXHAUSTED}).
 * {@code providerErrorRetryWindow}: {@code PROVIDER_ERROR} items classified within this window are reopened.
 */
public record SweepPolicy(Duration minAge, int batchSize, int maxAttempts, Duration providerErrorRetryWindow) {

    public SweepPolicy {
        requireNonNegative(minAge, "minAge");
        requireNonNegative(providerErrorRetryWindow, "providerErrorRetryWindow");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1, was " + batchSize);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be zero or positive, was " + value);
        }
    }
}
