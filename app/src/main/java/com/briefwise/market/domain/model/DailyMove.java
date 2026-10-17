package com.briefwise.market.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** Change of the latest close versus the previous available close. {@code changePercent} is in percent. */
public record DailyMove(Ticker ticker, LocalDate date, BigDecimal close, BigDecimal previousClose,
                        BigDecimal change, BigDecimal changePercent) {

    public DailyMove {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(previousClose, "previousClose");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(changePercent, "changePercent");
    }

    public static DailyMove between(Ticker ticker, DailyPrice previous, DailyPrice latest) {
        if (!latest.date().isAfter(previous.date())) {
            throw new IllegalArgumentException(
                    "Latest price (" + latest.date() + ") must be after previous (" + previous.date() + ")");
        }
        BigDecimal change = latest.close().subtract(previous.close());
        BigDecimal percent = change.multiply(BigDecimal.valueOf(100))
                .divide(previous.close(), 4, RoundingMode.HALF_UP);
        return new DailyMove(ticker, latest.date(), latest.close(), previous.close(), change, percent);
    }
}
