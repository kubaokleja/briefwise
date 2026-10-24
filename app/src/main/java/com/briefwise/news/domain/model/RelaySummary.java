package com.briefwise.news.domain.model;

/**
 * Result of one relay run. {@code pending}: rows still unpublished afterwards. {@code brokerUnavailable}: a send
 * failed and the run stopped early; the rows stay pending for the next run.
 */
public record RelaySummary(int published, int pending, boolean brokerUnavailable) {}
