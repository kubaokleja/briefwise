package com.briefwise.aiops.domain.port;

import com.briefwise.aiops.domain.model.DailyUsage;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Outbound port: running totals per feature and day. */
public interface DailyUsageRepository {

    Optional<DailyUsage> find(LocalDate date, String feature);

    /** Atomically adds one call to the day's totals (creating the row if needed) and returns the new totals. */
    DailyUsage add(LocalDate date, String feature, TokenCount tokens, BigDecimal costUsd);

    /** All rows between the dates (inclusive). */
    List<DailyUsage> findBetween(LocalDate from, LocalDate to);
}
