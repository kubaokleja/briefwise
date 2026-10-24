package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.RelaySummary;

/** Inbound port: send pending outbox rows to Kafka. Implemented by a package-private service. */
public interface OutboxRelay {

    /**
     * Sends pending rows oldest first and marks each published after the broker ack (at least once). Stops at the
     * first failed send; the remaining rows are sent on the next run. Safe to run concurrently.
     */
    RelaySummary relayPending();
}
