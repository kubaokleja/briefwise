package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.SweepSummary;

/** Inbound port: the sweep of items stuck in {@code RECEIVED}. Implemented by a package-private service. */
public interface PendingClassification {

    /**
     * Reopens recent {@code PROVIDER_ERROR} items, then processes old {@code RECEIVED} items whose outbox row is
     * published, oldest first, with the same code gate and classifier as the consumer. Safe to run concurrently with
     * the consumer: every result is a conditional write.
     */
    SweepSummary classifyPending();
}
