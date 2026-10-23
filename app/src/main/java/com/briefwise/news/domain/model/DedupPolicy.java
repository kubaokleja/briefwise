package com.briefwise.news.domain.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Near-duplicate rule (D1, ADR 0009): same ticker, published within {@code window} of each other, and trigram
 * headline similarity of at least {@code headlineSimilarity} (0..1, pg_trgm {@code similarity()}).
 */
public record DedupPolicy(double headlineSimilarity, Duration window) {

    public DedupPolicy {
        if (!(headlineSimilarity > 0 && headlineSimilarity <= 1)) {
            throw new IllegalArgumentException("headlineSimilarity must be in (0, 1], was " + headlineSimilarity);
        }
        Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
    }
}
