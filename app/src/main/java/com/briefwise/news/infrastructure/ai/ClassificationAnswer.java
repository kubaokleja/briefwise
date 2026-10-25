package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.Ticker;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The model's answer; {@code BeanOutputConverter} turns this record into the JSON schema the provider enforces
 * (native structured output, ADR 0012). {@code rationale} comes first, so the model states its reason before it
 * commits to the labels.
 */
@JsonPropertyOrder({"rationale", "sentiment", "relevance", "eventType", "affectedTickers"})
record ClassificationAnswer(String rationale, Sentiment sentiment, Relevance relevance, EventType eventType,
                            List<String> affectedTickers) {

    /**
     * Checks what the schema cannot: every affected ticker is one of the item's tickers (the schema stays the same
     * for every item, so its grammar stays cached).
     *
     * @throws IllegalArgumentException if a field is missing, or a ticker is not the item's
     */
    NewsClassification toClassification(NewsItem item, String promptVersion) {
        if (sentiment == null || relevance == null || eventType == null) {
            throw new IllegalArgumentException("Missing label in " + this);
        }
        if (affectedTickers == null || affectedTickers.isEmpty()) {
            throw new IllegalArgumentException("No affected ticker in " + this);
        }
        Set<Ticker> affected = affectedTickers.stream()
                .map(symbol -> new Ticker(symbol.trim().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toSet());
        if (!item.tickers().containsAll(affected)) {
            throw new IllegalArgumentException("Affected tickers " + affected + " are not all in " + item.tickers());
        }
        return new NewsClassification(sentiment, relevance, eventType, affected,
                rationale == null ? "" : rationale.strip(), promptVersion);
    }
}
