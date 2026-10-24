package com.briefwise.news.domain.port;

import com.briefwise.news.domain.event.NewsIngested;
import com.briefwise.news.domain.model.OutboxMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Outbound port: the transactional outbox for {@code news.raw} (ADR 0011). */
public interface NewsOutbox {

    /** Must run inside the transaction that stores the news item, so both are committed or neither is. */
    void append(NewsIngested event);

    /**
     * Oldest pending rows first, locked until the current transaction ends; rows locked by another relay are
     * skipped. Must run inside a transaction.
     */
    List<OutboxMessage> lockPending(int limit);

    void markPublished(UUID id, Instant publishedAt);

    void recordFailure(UUID id, String error);

    int countPending();
}
