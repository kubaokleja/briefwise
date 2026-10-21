package com.briefwise.aiops.domain.model;

import com.briefwise.shared.domain.model.TokenCount;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * One LLM call as reported by the calling adapter. {@code response} is empty when the call failed;
 * {@code fallback} is true when the caller used its fallback result instead of the model's answer.
 */
public record NewGeneration(String feature, String model, String promptVersion, String prompt,
                            Optional<String> response, TokenCount tokens, Duration latency, boolean fallback) {

    public NewGeneration {
        requireText(feature, "feature");
        requireText(model, "model");
        requireText(promptVersion, "promptVersion");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(latency, "latency");
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
