package com.briefwise.news.domain.model;

/**
 * Lifecycle of a news item. {@code RECEIVED} after ingest; {@code CLASSIFIED} or {@code UNCLASSIFIED} (LLM fallback)
 * after classification (M1 slice 4); {@code SKIPPED} is never classified, e.g. a near-duplicate (ADR 0009).
 */
public enum NewsStatus {
    RECEIVED,
    CLASSIFIED,
    UNCLASSIFIED,
    SKIPPED
}
