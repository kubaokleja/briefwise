package com.briefwise.shared.domain.model;

/**
 * Tokens used by one LLM call. Input to {@code AiopsFacade.checkAndRecordUsage()} after every call.
 * {@code promptTokens} are the uncached input tokens; with prompt caching (ADR 0012) the cached prefix is counted
 * separately as {@code cacheWriteTokens} (first call, priced above input) or {@code cacheReadTokens} (later calls,
 * priced far below input), as the provider reports them.
 */
public record TokenCount(int promptTokens, int completionTokens, int cacheWriteTokens, int cacheReadTokens) {

    public TokenCount {
        if (promptTokens < 0 || completionTokens < 0 || cacheWriteTokens < 0 || cacheReadTokens < 0) {
            throw new IllegalArgumentException("Token counts must not be negative");
        }
    }

    /** A call without prompt caching. */
    public TokenCount(int promptTokens, int completionTokens) {
        this(promptTokens, completionTokens, 0, 0);
    }

    public int total() {
        return promptTokens + completionTokens + cacheWriteTokens + cacheReadTokens;
    }
}
