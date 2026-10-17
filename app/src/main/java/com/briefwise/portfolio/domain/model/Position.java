package com.briefwise.portfolio.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.util.Objects;

/** A holding: how many units of a ticker, and its weight in the portfolio (0 < weight <= 1). */
public record Position(Ticker ticker, BigDecimal quantity, BigDecimal weight) {

    public Position {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(weight, "weight");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        if (weight.signum() <= 0 || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Weight must be in (0, 1]: " + weight);
        }
    }
}
