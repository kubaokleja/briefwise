package com.briefwise.news.domain.model;

/** How many outbox rows the relay claims per transaction. */
public record RelayPolicy(int batchSize) {

    public RelayPolicy {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1, was " + batchSize);
        }
    }
}
