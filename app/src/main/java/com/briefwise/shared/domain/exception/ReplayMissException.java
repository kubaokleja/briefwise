package com.briefwise.shared.domain.exception;

/**
 * The {@code replay} provider has no recorded answer for a call and {@code on-miss} is {@code fail} (ADR 0015): the
 * model is not called. Callers defer the work (e.g. the news item stays {@code RECEIVED}); a test that expected a
 * recorded answer fails on the missing result.
 */
public class ReplayMissException extends RuntimeException {

    private final String feature;

    public ReplayMissException(String feature, String key) {
        super("No replay entry for '" + feature + "' (key " + key + ") and on-miss is fail");
        this.feature = feature;
    }

    public String feature() {
        return feature;
    }
}
