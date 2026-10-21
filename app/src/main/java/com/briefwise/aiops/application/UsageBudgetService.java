package com.briefwise.aiops.application;

import com.briefwise.aiops.domain.model.BudgetStatus;
import com.briefwise.aiops.domain.model.CostSummary;
import com.briefwise.aiops.domain.model.DailyBudgets;
import com.briefwise.aiops.domain.model.DailyUsage;
import com.briefwise.aiops.domain.model.PriceList;
import com.briefwise.aiops.domain.port.DailyUsageRepository;
import com.briefwise.aiops.domain.port.UsageBudget;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
class UsageBudgetService implements UsageBudget {

    private final DailyUsageRepository usage;
    private final DailyBudgets budgets;
    private final PriceList prices;
    private final Clock clock;

    UsageBudgetService(DailyUsageRepository usage, DailyBudgets budgets, PriceList prices, Clock clock) {
        this.usage = usage;
        this.budgets = budgets;
        this.prices = prices;
        this.clock = clock;
    }

    @Override
    public BudgetStatus ensureAvailable(String feature) {
        BigDecimal limit = budgets.limitFor(feature);
        LocalDate today = today();
        DailyUsage spent = usage.find(today, feature).orElseGet(() -> DailyUsage.none(today, feature));
        return new BudgetStatus(feature, today, spent.costUsd(), limit).requireAvailable();
    }

    @Override
    public BudgetStatus recordUsage(String feature, String model, TokenCount tokens) {
        BigDecimal limit = budgets.limitFor(feature);
        BigDecimal cost = prices.priceOf(model).costOf(tokens);
        DailyUsage totals = usage.add(today(), feature, tokens, cost);
        return new BudgetStatus(feature, totals.date(), totals.costUsd(), limit);
    }

    @Override
    public CostSummary summarize(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' (" + from + ") must not be after 'to' (" + to + ")");
        }
        return CostSummary.of(from, to, usage.findBetween(from, to));
    }

    /** Budget days are UTC dates (ADR 0008), whatever zone the clock is in. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
