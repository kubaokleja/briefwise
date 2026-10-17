package com.briefwise.shared.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** Exchange ticker symbol, normalised to upper case (e.g. {@code AAPL}, {@code BRK.B}). */
public record Ticker(String symbol) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9.\\-]{0,9}");

    public Ticker {
        if (symbol == null) {
            throw new IllegalArgumentException("Ticker symbol must not be null");
        }
        symbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(symbol).matches()) {
            throw new IllegalArgumentException("Invalid ticker symbol: '" + symbol + "'");
        }
    }

    @Override
    public String toString() {
        return symbol;
    }
}
