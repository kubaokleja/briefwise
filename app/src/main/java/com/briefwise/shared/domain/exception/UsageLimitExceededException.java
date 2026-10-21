package com.briefwise.shared.domain.exception;

import java.math.BigDecimal;

/**
 * A feature has used its daily LLM budget (ADR 0008). Thrown before a call, never after one. Inbound adapters
 * translate it to HTTP 429; job runners mark the step as skipped.
 */
public class UsageLimitExceededException extends RuntimeException {

    private final String feature;

    public UsageLimitExceededException(String feature, BigDecimal spentUsd, BigDecimal limitUsd) {
        super("Daily LLM budget for '" + feature + "' is used up: spent " + spentUsd.toPlainString()
                + " USD of " + limitUsd.toPlainString() + " USD");
        this.feature = feature;
    }

    public String feature() {
        return feature;
    }
}
