package com.briefwise.aiops.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Totals of one feature on one UTC day. */
public record DailyUsage(LocalDate date, String feature, int calls, long promptTokens, long completionTokens,
                         BigDecimal costUsd) {

    public DailyUsage {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(costUsd, "costUsd");
    }

    public static DailyUsage none(LocalDate date, String feature) {
        return new DailyUsage(date, feature, 0, 0, 0, BigDecimal.ZERO);
    }
}
