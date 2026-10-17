package com.briefwise.portfolio.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Synthetic client identifier. The only client reference that may ever appear in prompts. */
public record ClientId(UUID value) {

    public ClientId {
        Objects.requireNonNull(value, "value");
    }
}
