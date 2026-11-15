package com.briefwise.research.domain.model;

import java.util.Objects;

/**
 * One extracted part of a filing. {@code itemId} is the item as named in {@link SectionPolicy} ({@code 1A},
 * {@code II-1A}, {@code 2.02}, {@code EX-99.1}); {@code text} is plain text, tables as {@code cell | cell} rows.
 */
public record FilingSection(String itemId, String title, String text) {

    public FilingSection {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        Objects.requireNonNull(title, "title");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text of section " + itemId + " must not be blank");
        }
    }
}
