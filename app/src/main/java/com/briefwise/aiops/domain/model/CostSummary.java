package com.briefwise.aiops.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** LLM usage per feature over a date range, most expensive feature first. */
public record CostSummary(LocalDate from, LocalDate to, List<FeatureCost> byFeature) {

    public CostSummary {
        byFeature = List.copyOf(byFeature);
    }

    public record FeatureCost(String feature, int calls, long promptTokens, long completionTokens,
                              BigDecimal costUsd) {}

    public static CostSummary of(LocalDate from, LocalDate to, List<DailyUsage> days) {
        Map<String, FeatureCost> totals = new TreeMap<>();
        for (DailyUsage day : days) {
            totals.merge(day.feature(),
                    new FeatureCost(day.feature(), day.calls(), day.promptTokens(), day.completionTokens(),
                            day.costUsd()),
                    (a, b) -> new FeatureCost(a.feature(), a.calls() + b.calls(),
                            a.promptTokens() + b.promptTokens(), a.completionTokens() + b.completionTokens(),
                            a.costUsd().add(b.costUsd())));
        }
        List<FeatureCost> sorted = totals.values().stream()
                .sorted(Comparator.comparing(FeatureCost::costUsd).reversed())
                .toList();
        return new CostSummary(from, to, sorted);
    }

    public BigDecimal totalUsd() {
        return byFeature.stream().map(FeatureCost::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int totalCalls() {
        return byFeature.stream().mapToInt(FeatureCost::calls).sum();
    }
}
