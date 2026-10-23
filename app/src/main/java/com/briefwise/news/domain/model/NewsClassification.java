package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;
import java.util.Set;

/**
 * What the classifier said about one news item (M1 slice 4). {@code promptVersion} ties the result to the prompt
 * that produced it, so results from different prompt versions are never mixed up.
 */
public record NewsClassification(Sentiment sentiment, Relevance relevance, EventType eventType,
                                 Set<Ticker> tickers, String rationale, String promptVersion) {

    public NewsClassification {
        Objects.requireNonNull(sentiment, "sentiment");
        Objects.requireNonNull(relevance, "relevance");
        Objects.requireNonNull(eventType, "eventType");
        tickers = Set.copyOf(Objects.requireNonNull(tickers, "tickers"));
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("promptVersion must not be blank");
        }
    }
}
