package com.briefwise.aiops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.aiops.domain.model.CostSummary.FeatureCost;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CostSummaryTest {

    private final LocalDate d1 = LocalDate.parse("2026-10-20");
    private final LocalDate d2 = LocalDate.parse("2026-10-21");

    @Test
    void sums_days_per_feature_and_sorts_most_expensive_first() {
        CostSummary summary = CostSummary.of(d1, d2, List.of(
                new DailyUsage(d1, "news-classification", 10, 12_000, 2_000, new BigDecimal("0.0022")),
                new DailyUsage(d1, "brief", 1, 10_000, 2_000, new BigDecimal("0.04")),
                new DailyUsage(d2, "news-classification", 5, 6_000, 1_000, new BigDecimal("0.0011"))));

        assertThat(summary.byFeature()).containsExactly(
                new FeatureCost("brief", 1, 10_000, 2_000, new BigDecimal("0.04")),
                new FeatureCost("news-classification", 15, 18_000, 3_000, new BigDecimal("0.0033")));
        assertThat(summary.totalCalls()).isEqualTo(16);
        assertThat(summary.totalUsd()).isEqualByComparingTo("0.0433");
    }

    @Test
    void no_usage_is_an_empty_summary_with_zero_totals() {
        CostSummary summary = CostSummary.of(d1, d2, List.of());

        assertThat(summary.byFeature()).isEmpty();
        assertThat(summary.totalCalls()).isZero();
        assertThat(summary.totalUsd()).isEqualByComparingTo("0");
    }
}
