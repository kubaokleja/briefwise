package com.briefwise.aiops.domain.model;

import java.time.Instant;
import java.util.Objects;

/** A recorded, usable model answer that the {@code replay} provider returns instead of calling the model. */
public record ReplayEntry(ReplayKey key, String model, String response, Instant recordedAt) {

    public ReplayEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("response must not be blank");
        }
    }
}
