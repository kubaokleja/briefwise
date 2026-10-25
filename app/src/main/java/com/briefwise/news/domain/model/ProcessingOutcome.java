package com.briefwise.news.domain.model;

/**
 * What handling one {@code news.raw} message did. {@code ALREADY_PROCESSED}: the item was no longer {@code RECEIVED}
 * (a duplicate delivery, K3 in ADR 0011). {@code NOT_HELD}: none of its tickers is held any more, so it was
 * {@code SKIPPED} without an LLM call. {@code UNCLASSIFIED}: fallback (invalid output, provider error or suspected
 * injection). {@code PENDING}: the classifier deferred (e.g. budget used up) and the item stays
 * {@code RECEIVED}.
 */
public enum ProcessingOutcome {
    CLASSIFIED,
    UNCLASSIFIED,
    NOT_HELD,
    ALREADY_PROCESSED,
    PENDING
}
