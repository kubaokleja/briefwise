package com.briefwise.research.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC accession number, the unique id of one filing ({@code 0000320193-25-000079}). Accepts the dashed form or the
 * 18 digits without dashes (as in archive folder names); always stored dashed.
 */
public record AccessionNo(String value) {

    private static final Pattern DASHED = Pattern.compile("[0-9]{10}-[0-9]{2}-[0-9]{6}");
    private static final Pattern PLAIN = Pattern.compile("([0-9]{10})([0-9]{2})([0-9]{6})");

    public AccessionNo {
        if (value == null) {
            throw new IllegalArgumentException("Accession number must not be null");
        }
        value = value.trim();
        Matcher plain = PLAIN.matcher(value);
        if (plain.matches()) {
            value = plain.group(1) + "-" + plain.group(2) + "-" + plain.group(3);
        } else if (!DASHED.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid accession number: '" + value + "'");
        }
    }

    /** The 18 digits, as used in the archive folder of the filing. */
    public String withoutDashes() {
        return value.replace("-", "");
    }

    @Override
    public String toString() {
        return value;
    }
}
