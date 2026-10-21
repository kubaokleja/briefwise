package com.briefwise.aiops.domain.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Daily spending limit in USD per feature (ADR 0008). */
public record DailyBudgets(Map<String, BigDecimal> limitsUsd) {

    public DailyBudgets {
        limitsUsd = Map.copyOf(Objects.requireNonNull(limitsUsd, "limitsUsd"));
        limitsUsd.forEach((feature, limit) -> {
            if (limit.signum() < 0) {
                throw new IllegalArgumentException("Budget for '" + feature + "' must not be negative");
            }
        });
    }

    /** @throws NoBudgetConfiguredException if the feature has no limit: fail closed, never spend without one */
    public BigDecimal limitFor(String feature) {
        BigDecimal limit = limitsUsd.get(feature);
        if (limit == null) {
            throw new NoBudgetConfiguredException(feature);
        }
        return limit;
    }
}
