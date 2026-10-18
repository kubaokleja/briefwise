package com.briefwise.shared.domain.model;

/** Tokens used by one LLM call. Input to {@code AiopsFacade.checkAndRecordUsage()} after every call. */
public record TokenCount(int promptTokens, int completionTokens) {

    public TokenCount {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("Token counts must not be negative");
        }
    }

    public int total() {
        return promptTokens + completionTokens;
    }
}
