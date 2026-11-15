package com.briefwise.research.domain.model;

import java.util.Arrays;
import java.util.Optional;

/** The forms ingested (ADR 0025). Amendments ({@code 10-K/A}) and all other forms are not mapped. */
public enum FilingType {
    TEN_K("10-K"),
    TEN_Q("10-Q"),
    EIGHT_K("8-K");

    private final String form;

    FilingType(String form) {
        this.form = form;
    }

    /** The EDGAR form code, e.g. {@code 10-K}. */
    public String form() {
        return form;
    }

    /** Exact match on the EDGAR form code; empty for amendments and other forms. */
    public static Optional<FilingType> fromForm(String form) {
        if (form == null) {
            return Optional.empty();
        }
        String trimmed = form.trim();
        return Arrays.stream(values()).filter(t -> t.form.equalsIgnoreCase(trimmed)).findFirst();
    }
}
