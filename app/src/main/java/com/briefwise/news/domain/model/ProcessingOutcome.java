package com.briefwise.news.domain.model;

/**
 * What handling one {@code news.raw} message did. {@code ALREADY_PROCESSED}: the item was no longer {@code RECEIVED}
 * (a duplicate delivery, K3 in ADR 0011). {@code PENDING}: the classifier gave no result and the item stays
 * {@code RECEIVED} (the placeholder classifier until M1 slice 4).
 */
public enum ProcessingOutcome {
    CLASSIFIED,
    ALREADY_PROCESSED,
    PENDING
}
