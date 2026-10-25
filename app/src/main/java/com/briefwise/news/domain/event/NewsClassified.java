package com.briefwise.news.domain.event;

import com.briefwise.news.domain.model.Relevance;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A news item was classified (DESIGN 6.4). Published in-process after the result is stored; HIGH relevance feeds the
 * daily summary (slice 7). No Kafka topic until a second consumer needs it.
 */
public record NewsClassified(UUID newsId, Relevance relevance, Instant occurredAt) {

    public NewsClassified {
        Objects.requireNonNull(newsId, "newsId");
        Objects.requireNonNull(relevance, "relevance");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
