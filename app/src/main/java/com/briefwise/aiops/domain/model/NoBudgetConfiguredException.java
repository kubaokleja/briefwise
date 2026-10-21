package com.briefwise.aiops.domain.model;

/** A feature asks for LLM budget but has no entry in {@code briefwise.aiops.daily-budget-usd}. */
public class NoBudgetConfiguredException extends RuntimeException {

    public NoBudgetConfiguredException(String feature) {
        super("No daily budget configured for feature '" + feature + "' (briefwise.aiops.daily-budget-usd)");
    }
}
