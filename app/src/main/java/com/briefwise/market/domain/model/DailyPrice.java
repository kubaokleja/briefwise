package com.briefwise.market.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record DailyPrice(LocalDate date, BigDecimal close, long volume) {

    public DailyPrice {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(close, "close");
        if (close.signum() <= 0) {
            throw new IllegalArgumentException("Close must be positive: " + close);
        }
        if (volume < 0) {
            throw new IllegalArgumentException("Volume must not be negative: " + volume);
        }
    }
}
