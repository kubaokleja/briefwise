package com.briefwise.news.domain.model;

import java.util.Objects;

/**
 * A compliance rule a summary text broke, as reported by {@code compliance} ({@code rule} is its rule id). Sent back
 * to the model on the re-ask, and stored with a summary that still breaks it (template fallback only).
 */
public record SummaryViolation(String rule, String offendingText) {

    public SummaryViolation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(offendingText, "offendingText");
    }
}
