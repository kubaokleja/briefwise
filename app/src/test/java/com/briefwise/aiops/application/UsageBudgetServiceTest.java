package com.briefwise.aiops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.briefwise.aiops.domain.model.BudgetStatus;
import com.briefwise.aiops.domain.model.DailyBudgets;
import com.briefwise.aiops.domain.model.DailyUsage;
import com.briefwise.aiops.domain.model.ModelPrice;
import com.briefwise.aiops.domain.model.NoBudgetConfiguredException;
import com.briefwise.aiops.domain.model.PriceList;
import com.briefwise.aiops.domain.port.DailyUsageRepository;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UsageBudgetServiceTest {

    private static final String FEATURE = "news-classification";
    private static final String MODEL = "claude-haiku-5-5";

    // 00:30 in Zurich on 21 Oct is still 20 Oct in UTC: the budget day must follow UTC, not the clock's zone.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T22:30:00Z"), ZoneId.of("Europe/Zurich"));
    private final LocalDate utcToday = LocalDate.parse("2026-10-20");

    private final DailyUsageRepository usage = mock(DailyUsageRepository.class);
    private final UsageBudgetService service = new UsageBudgetService(usage,
            new DailyBudgets(Map.of(FEATURE, new BigDecimal("0.10"))),
            new PriceList(Map.of(MODEL, new ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.50")))),
            clock);

    private DailyUsage spent(String costUsd) {
        return new DailyUsage(utcToday, FEATURE, 3, 3_000, 600, new BigDecimal(costUsd));
    }

    @Test
    void no_usage_yet_means_the_full_budget_is_available_on_the_utc_day() {
        when(usage.find(utcToday, FEATURE)).thenReturn(Optional.empty());

        BudgetStatus status = service.ensureAvailable(FEATURE);

        assertThat(status.date()).isEqualTo(utcToday);
        assertThat(status.remainingUsd()).isEqualByComparingTo("0.10");
    }

    @Test
    void used_up_budget_refuses_the_call() {
        when(usage.find(utcToday, FEATURE)).thenReturn(Optional.of(spent("0.10")));

        assertThatThrownBy(() -> service.ensureAvailable(FEATURE)).isInstanceOf(UsageLimitExceededException.class);
    }

    @Test
    void feature_without_budget_is_refused_before_touching_storage() {
        assertThatThrownBy(() -> service.ensureAvailable("brief")).isInstanceOf(NoBudgetConfiguredException.class);
        verifyNoInteractions(usage);
    }

    @Test
    void record_adds_the_priced_call_and_reports_overshoot_without_throwing() {
        TokenCount tokens = new TokenCount(1_200, 200);
        BigDecimal cost = new BigDecimal("0.00022000");
        when(usage.add(utcToday, FEATURE, tokens, cost)).thenReturn(spent("0.10010"));

        BudgetStatus status = service.recordUsage(FEATURE, MODEL, tokens);

        verify(usage).add(utcToday, FEATURE, tokens, cost);
        assertThat(status.exhausted()).isTrue();
        assertThat(status.spentUsd()).isEqualByComparingTo("0.10010");
    }

    @Test
    void summary_rejects_an_inverted_range() {
        assertThatThrownBy(() -> service.summarize(utcToday, utcToday.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(usage);
    }
}
