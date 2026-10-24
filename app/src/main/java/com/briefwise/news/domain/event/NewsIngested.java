package com.briefwise.news.domain.event;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A news item was stored as {@code RECEIVED} and waits for classification (DESIGN 6.4). {@code ticker} is the ticker
 * it was first fetched for and becomes the Kafka key (K2, ADR 0011); the item may concern more tickers later.
 */
public record NewsIngested(UUID newsId, Ticker ticker, Instant occurredAt) {

    public NewsIngested {
        Objects.requireNonNull(newsId, "newsId");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
