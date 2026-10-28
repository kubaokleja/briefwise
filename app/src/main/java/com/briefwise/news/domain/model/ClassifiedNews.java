package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A {@code CLASSIFIED} item as read for portfolio selection (slice 7): the stored item fields plus its labels.
 * {@code affectedTickers} are the tickers the classifier named (ADR 0014), not every ticker the item was fetched for.
 */
public record ClassifiedNews(UUID id, String headline, String summary, String url, String source,
                             Instant publishedAt, Instant classifiedAt, Set<Ticker> affectedTickers,
                             Sentiment sentiment, Relevance relevance, EventType eventType, String rationale) {

    public ClassifiedNews {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(headline, "headline");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(classifiedAt, "classifiedAt");
        affectedTickers = Set.copyOf(Objects.requireNonNull(affectedTickers, "affectedTickers"));
        Objects.requireNonNull(sentiment, "sentiment");
        Objects.requireNonNull(relevance, "relevance");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(rationale, "rationale");
    }
}
