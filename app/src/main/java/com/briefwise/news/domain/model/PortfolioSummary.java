package com.briefwise.news.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The daily news summary of one portfolio (M1 slice 7, ADR 0017); at most one per portfolio and UTC date, replaced
 * when it is regenerated. Always AI-marked in the UI. {@code fallback}: built from a template because no compliant
 * model answer was available; {@code model} and {@code promptVersion} are then empty. No points: nothing relevant
 * was published in the window (no LLM call). {@code disclaimer}: the text shown with it, appended in code.
 * {@code violations}: rules a fallback still breaks (e.g. a headline quoted verbatim); empty otherwise.
 */
public record PortfolioSummary(UUID id, UUID portfolioId, LocalDate summaryDate, Instant generatedAt,
                               List<SummaryPoint> points, boolean fallback, Optional<String> model,
                               Optional<String> promptVersion, String disclaimer,
                               List<SummaryViolation> violations) {

    public PortfolioSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(summaryDate, "summaryDate");
        Objects.requireNonNull(generatedAt, "generatedAt");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(disclaimer, "disclaimer");
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (fallback && model.isPresent()) {
            throw new IllegalArgumentException("A template fallback has no model");
        }
    }

    /** Nothing relevant was published in the window. */
    public boolean empty() {
        return points.isEmpty();
    }
}
