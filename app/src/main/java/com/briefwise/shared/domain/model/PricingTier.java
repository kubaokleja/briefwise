package com.briefwise.shared.domain.model;

/**
 * How a call is billed. {@code STANDARD}: a synchronous Messages API call. {@code BATCH}: a request in a Message Batch
 * (asynchronous, results within 24 h), billed at half the price of every token kind; used for evals (DESIGN 13,
 * ADR 0018).
 */
public enum PricingTier {
    STANDARD,
    BATCH
}
