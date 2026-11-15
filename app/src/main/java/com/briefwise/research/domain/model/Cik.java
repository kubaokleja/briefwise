package com.briefwise.research.domain.model;

import java.util.regex.Pattern;

/** SEC Central Index Key of an issuer, normalised to 10 digits with leading zeros (the submissions API form). */
public record Cik(String value) {

    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,10}");

    public Cik {
        if (value == null) {
            throw new IllegalArgumentException("CIK must not be null");
        }
        String trimmed = value.trim();
        if (!DIGITS.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid CIK: '" + value + "'");
        }
        value = "0".repeat(10 - trimmed.length()) + trimmed;
    }

    /** Without leading zeros, as in archive paths ({@code /Archives/edgar/data/320193/...}). */
    public String unpadded() {
        String stripped = value.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    @Override
    public String toString() {
        return value;
    }
}
