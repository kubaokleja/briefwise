package com.briefwise.aiops;

import com.briefwise.aiops.domain.model.BudgetStatus;
import com.briefwise.aiops.domain.model.CostSummary;
import com.briefwise.aiops.domain.model.NewGeneration;
import com.briefwise.aiops.domain.port.GenerationLog;
import com.briefwise.aiops.domain.port.UsageBudget;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code aiops}. Every LLM adapter calls, in this order:
 * {@link #ensureBudgetAvailable} before the call, then {@link #recordGeneration} and {@link #checkAndRecordUsage}
 * after it (ADR 0008). {@code findReplay} follows in M1 slice 5. No logic here: delegate and map.
 */
@Component
public class AiopsFacade {

    private final GenerationLog generationLog;
    private final UsageBudget usageBudget;

    AiopsFacade(GenerationLog generationLog, UsageBudget usageBudget) {
        this.generationLog = generationLog;
        this.usageBudget = usageBudget;
    }

    /**
     * Writes the call to {@code ai_generation_log} with its cost and the current correlation ID.
     *
     * @return the ID of the log entry
     * @throws IllegalArgumentException if a required field is blank or the latency is negative
     * @throws com.briefwise.aiops.domain.model.UnpricedModelException if the model has no configured price
     */
    public UUID recordGeneration(GenerationEntry entry) {
        return generationLog.record(new NewGeneration(entry.feature(), entry.model(), entry.promptVersion(),
                entry.prompt(), entry.response(), entry.tokens(), entry.latency(), entry.fallback()));
    }

    /**
     * Code gate: call before every LLM call.
     *
     * @throws com.briefwise.shared.domain.exception.UsageLimitExceededException if today's budget is used up
     * @throws com.briefwise.aiops.domain.model.NoBudgetConfiguredException if the feature has no budget
     */
    public BudgetView ensureBudgetAvailable(String feature) {
        return toView(usageBudget.ensureAvailable(feature));
    }

    /**
     * Adds a finished call to today's totals and returns the new status. Does not throw when the budget is now
     * exceeded; check {@link BudgetView#exhausted()} or let the next {@link #ensureBudgetAvailable} refuse.
     *
     * @throws com.briefwise.aiops.domain.model.UnpricedModelException if the model has no configured price
     * @throws com.briefwise.aiops.domain.model.NoBudgetConfiguredException if the feature has no budget
     */
    public BudgetView checkAndRecordUsage(String feature, String model, TokenCount tokens) {
        return toView(usageBudget.recordUsage(feature, model, tokens));
    }

    /**
     * Usage per feature between the UTC dates (inclusive).
     *
     * @throws IllegalArgumentException if {@code from} is after {@code to}
     */
    public CostSummaryView getCostSummary(LocalDate from, LocalDate to) {
        CostSummary summary = usageBudget.summarize(from, to);
        List<FeatureCostView> byFeature = summary.byFeature().stream()
                .map(f -> new FeatureCostView(f.feature(), f.calls(), f.promptTokens(), f.completionTokens(),
                        f.costUsd()))
                .toList();
        return new CostSummaryView(summary.from(), summary.to(), byFeature, summary.totalCalls(),
                summary.totalUsd());
    }

    private static BudgetView toView(BudgetStatus status) {
        return new BudgetView(status.feature(), status.date(), status.spentUsd(), status.limitUsd(),
                status.remainingUsd(), status.exhausted());
    }

    /** One LLM call. {@code response} is empty when the call failed; {@code fallback} when its fallback was used. */
    public record GenerationEntry(String feature, String model, String promptVersion, String prompt,
                                  Optional<String> response, TokenCount tokens, Duration latency,
                                  boolean fallback) {}

    public record BudgetView(String feature, LocalDate date, BigDecimal spentUsd, BigDecimal limitUsd,
                             BigDecimal remainingUsd, boolean exhausted) {}

    public record FeatureCostView(String feature, int calls, long promptTokens, long completionTokens,
                                  BigDecimal costUsd) {}

    public record CostSummaryView(LocalDate from, LocalDate to, List<FeatureCostView> byFeature, int totalCalls,
                                  BigDecimal totalUsd) {}
}
