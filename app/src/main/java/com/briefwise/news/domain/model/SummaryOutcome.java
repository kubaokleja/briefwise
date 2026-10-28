package com.briefwise.news.domain.model;

/** What summarising one portfolio did. */
public enum SummaryOutcome {
    /** A compliant model summary was stored. */
    GENERATED,
    /** The template summary was stored ({@code isFallback}). */
    FALLBACK,
    /** Nothing relevant in the window: an empty summary was stored without an LLM call. */
    EMPTY,
    /** The regeneration gate kept today's summary: nothing new since it was made. */
    UNCHANGED,
    /** The model was not called (budget, no chat model, replay miss); the previous summary stays. */
    DEFERRED
}
