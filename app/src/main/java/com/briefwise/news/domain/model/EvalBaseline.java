package com.briefwise.news.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A run as the regression gate compares it (ADR 0018, ADR 0022): the committed reference ({@code baseline.json}) or
 * the current run in the same shape ({@link EvalReport#asBaseline()}). {@code goldenSha256} is empty for a baseline
 * recorded before ADR 0022; such a baseline is not comparable.
 */
public record EvalBaseline(String model, String promptVersion, int cases, Optional<String> goldenSha256,
                           double sentimentMacroF1) {

    public EvalBaseline {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(goldenSha256, "goldenSha256");
        if (cases < 0) {
            throw new IllegalArgumentException("cases must not be negative");
        }
    }
}
