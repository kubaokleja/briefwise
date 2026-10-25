package com.briefwise.news.domain.model;

/** Main event of a news item. {@code ANALYST}: sell-side rating or price-target actions (ADR 0014). */
public enum EventType {
    EARNINGS,
    GUIDANCE,
    M_AND_A,
    REGULATORY,
    LITIGATION,
    MANAGEMENT,
    MACRO,
    PRODUCT,
    ANALYST,
    OTHER
}
