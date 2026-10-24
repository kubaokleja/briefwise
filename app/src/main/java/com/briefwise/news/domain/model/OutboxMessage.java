package com.briefwise.news.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A pending outbox row as the relay sees it: where to send it and what. The payload is opaque to the relay. */
public record OutboxMessage(UUID id, String topic, String key, String payload, Optional<String> correlationId) {

    public OutboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
