package com.briefwise.aiops.domain.model;

import com.briefwise.shared.domain.model.TokenCount;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One LLM call as reported by the calling adapter. {@code response} is empty when the call failed;
 * {@code fallback} is true when the caller used its fallback result instead of the model's answer.
 * {@code subjectId} is the business object the call was made for, e.g. the news item (ADR 0014).
 */
public record NewGeneration(String feature, String model, String promptVersion, String prompt,
                            Optional<String> response, TokenCount tokens, Duration latency, boolean fallback,
                            Optional<UUID> subjectId) {

    public NewGeneration {
        requireText(feature, "feature");
        requireText(model, "model");
        requireText(promptVersion, "promptVersion");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(subjectId, "subjectId");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
