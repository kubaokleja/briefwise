package com.briefwise.portfolio.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** An instrument's latest close and its change versus the previous close, from {@code market}; in percent. */
public record InstrumentMove(Ticker ticker, LocalDate date, BigDecimal close, BigDecimal changePercent) {

    public InstrumentMove {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(changePercent, "changePercent");
    }
}
