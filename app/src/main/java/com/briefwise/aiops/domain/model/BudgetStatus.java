package com.briefwise.aiops.domain.model;

import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** How much of a feature's daily budget is used on a UTC day. */
public record BudgetStatus(String feature, LocalDate date, BigDecimal spentUsd, BigDecimal limitUsd) {

    public BudgetStatus {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(spentUsd, "spentUsd");
        Objects.requireNonNull(limitUsd, "limitUsd");
    }

    /** Spent reaches the limit: the next call is refused. A call already running may overshoot once (ADR 0008). */
    public boolean exhausted() {
        return spentUsd.compareTo(limitUsd) >= 0;
    }

    public BigDecimal remainingUsd() {
        return limitUsd.subtract(spentUsd).max(BigDecimal.ZERO);
    }

    /** @throws UsageLimitExceededException if the budget is exhausted */
    public BudgetStatus requireAvailable() {
        if (exhausted()) {
            throw new UsageLimitExceededException(feature, spentUsd, limitUsd);
        }
        return this;
    }
}
