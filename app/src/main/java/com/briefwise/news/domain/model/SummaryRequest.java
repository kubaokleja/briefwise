package com.briefwise.news.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for one summary call. {@code news} is already ordered by importance. {@code corrections}: the rules the
 * previous draft broke (empty on the first call), so the model can rewrite those passages.
 */
public record SummaryRequest(UUID portfolioId, List<RelevantNews> news, List<SummaryViolation> corrections) {

    public SummaryRequest {
        Objects.requireNonNull(portfolioId, "portfolioId");
        news = List.copyOf(Objects.requireNonNull(news, "news"));
        if (news.isEmpty()) {
            throw new IllegalArgumentException("Nothing to summarise");
        }
        corrections = List.copyOf(Objects.requireNonNull(corrections, "corrections"));
    }
}
