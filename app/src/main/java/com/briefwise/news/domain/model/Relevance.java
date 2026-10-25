package com.briefwise.news.domain.model;

/**
 * How much the item matters to a holder of its most affected ticker, regardless of portfolio (ADR 0014); weighting by
 * portfolio happens in code when news is selected for one.
 */
public enum Relevance {
    LOW,
    MEDIUM,
    HIGH
}
