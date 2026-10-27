package com.briefwise.aiops.domain.model;

import java.util.Objects;

/**
 * Whether calls are answered from recordings ({@code briefwise.llm.provider=replay}) and what a miss does: {@code FAIL}
 * never calls the model (CI, demo), {@code FALLBACK} calls the configured chat model and records the answer.
 */
public record ReplayPolicy(boolean enabled, OnMiss onMiss) {

    public enum OnMiss { FAIL, FALLBACK }

    public ReplayPolicy {
        Objects.requireNonNull(onMiss, "onMiss");
    }

    public static ReplayPolicy disabled() {
        return new ReplayPolicy(false, OnMiss.FALLBACK);
    }
}
