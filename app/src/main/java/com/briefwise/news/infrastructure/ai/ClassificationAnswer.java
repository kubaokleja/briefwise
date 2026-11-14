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
     * for every item, so its grammar stays cached), and an empty list comes only with relevance {@code LOW}: an item
     * that is not materially about any of its tickers cannot matter to a holder (ADR 0023). Such an item never
     * reaches a portfolio's relevant news.
     *
     * @throws IllegalArgumentException if a field is missing, a ticker is not the item's, or no ticker is named for
     *                                  a {@code MEDIUM} / {@code HIGH} item
     */
    NewsClassification toClassification(NewsItem item, String promptVersion) {
        if (sentiment == null || relevance == null || eventType == null) {
            throw new IllegalArgumentException("Missing label in " + this);
        }
        if (affectedTickers == null) {
            throw new IllegalArgumentException("No affectedTickers in " + this);
        }
        if (affectedTickers.isEmpty() && relevance != Relevance.LOW) {
            throw new IllegalArgumentException("No affected ticker for a " + relevance + " item in " + this);
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
