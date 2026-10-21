package com.briefwise.aiops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetStatusTest {

    private final LocalDate day = LocalDate.parse("2026-10-21");

    private BudgetStatus status(String spent, String limit) {
        return new BudgetStatus("news-classification", day, new BigDecimal(spent), new BigDecimal(limit));
    }

    @Test
    void below_the_limit_is_available_with_the_difference_remaining() {
        BudgetStatus status = status("0.04", "0.10");

        assertThat(status.exhausted()).isFalse();
        assertThat(status.remainingUsd()).isEqualByComparingTo("0.06");
        assertThat(status.requireAvailable()).isSameAs(status);
    }

    @Test
    void reaching_the_limit_exactly_exhausts_the_budget() {
        assertThat(status("0.10", "0.10").exhausted()).isTrue();
    }

    @Test
    void overshoot_reports_zero_remaining_and_refuses_the_next_call() {
        BudgetStatus status = status("0.12", "0.10");

        assertThat(status.remainingUsd()).isEqualByComparingTo("0");
        assertThatThrownBy(status::requireAvailable)
                .isInstanceOf(UsageLimitExceededException.class)
                .hasMessageContaining("news-classification")
                .satisfies(e -> assertThat(((UsageLimitExceededException) e).feature())
                        .isEqualTo("news-classification"));
    }

    @Test
    void a_zero_budget_disables_the_feature() {
        assertThatThrownBy(() -> status("0", "0").requireAvailable())
                .isInstanceOf(UsageLimitExceededException.class);
    }

    @Test
    void feature_without_a_configured_budget_fails_closed() {
        DailyBudgets budgets = new DailyBudgets(Map.of("news-classification", new BigDecimal("0.10")));

        assertThat(budgets.limitFor("news-classification")).isEqualByComparingTo("0.10");
        assertThatThrownBy(() -> budgets.limitFor("brief"))
                .isInstanceOf(NoBudgetConfiguredException.class)
                .hasMessageContaining("brief");
    }
}
