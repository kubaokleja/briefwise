package com.briefwise.compliance.domain.model;

import java.util.Objects;

/** One factual passage of a generated text (e.g. a summary point) and how many sources it cites. */
public record Passage(String text, int citations) {

    public Passage {
        Objects.requireNonNull(text, "text");
        if (citations < 0) {
            throw new IllegalArgumentException("citations must not be negative, was " + citations);
        }
    }
}
