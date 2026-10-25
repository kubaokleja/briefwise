package com.briefwise.news.domain.model;

/**
 * Why an item fell back to {@code UNCLASSIFIED} (it stays in the feed without labels). {@code INVALID_OUTPUT}: the
 * model's answer did not fit the schema after the re-ask; {@code PROVIDER_ERROR}: the call failed after retries;
 * {@code INJECTION_SUSPECTED}: screening kept the item away from the LLM (ADR 0013).
 */
public enum UnclassifiedReason {
    INVALID_OUTPUT,
    PROVIDER_ERROR,
    INJECTION_SUSPECTED
}
