package com.briefwise.aiops;

import com.briefwise.aiops.domain.model.BudgetStatus;
import com.briefwise.aiops.domain.model.CostSummary;
import com.briefwise.aiops.domain.model.NewGeneration;
import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import com.briefwise.aiops.domain.port.GenerationLog;
import com.briefwise.aiops.domain.port.Replays;
import com.briefwise.aiops.domain.port.UsageBudget;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code aiops}. Every LLM adapter calls, in this order:
 * {@link #ensureBudgetAvailable} before the call, adds {@link #replay} to the call's advisors, then
 * {@link #recordGeneration} and {@link #checkAndRecordUsage} after it (ADR 0008), unless {@link #isReplayed} says the
 * answer came from a recording (ADR 0015). No logic here: delegate and map.
 */
@Component
public class AiopsFacade {

    private final GenerationLog generationLog;
    private final UsageBudget usageBudget;
    private final Replays replays;
    private final CallAdvisor replayAdvisor;

    AiopsFacade(GenerationLog generationLog, UsageBudget usageBudget, Replays replays,
                @Qualifier("replayAdvisor") CallAdvisor replayAdvisor) {
        this.generationLog = generationLog;
        this.usageBudget = usageBudget;
        this.replays = replays;
        this.replayAdvisor = replayAdvisor;
    }

    /**
     * The recorded answer for the request, from the committed fixtures or the generation log, whatever the provider.
     *
     * @throws IllegalArgumentException if a key part is blank
     */
    public Optional<String> findReplay(ReplayRequest request) {
        return replays.find(request.key()).map(ReplayEntry::response);
    }

    /**
     * Adds the replay advisor and this call's key to a {@code ChatClient} request:
     * {@code chatClient.prompt()...advisors(aiops.replay(request))}. With {@code briefwise.llm.provider=replay} the
     * advisor answers from a recording or, on a miss, throws
     * {@link com.briefwise.shared.domain.exception.ReplayMissException} ({@code on-miss: fail}) or calls the model
     * ({@code fallback}). With any other provider it passes the call on.
     */
    public Consumer<ChatClient.AdvisorSpec> replay(ReplayRequest request) {
        ReplayKey key = request.key();
        return advisors -> advisors.advisors(replayAdvisor)
                .param(ReplayKey.CONTEXT_PARAM, key)
                .param(ReplayKey.FEATURE_PARAM, request.feature());
    }

    /** True when the answer came from a recording: do not log it again and do not count it against the budget. */
    public boolean isReplayed(ChatResponse response) {
        return response != null && Boolean.TRUE.equals(response.getMetadata().get(ReplayKey.REPLAYED_METADATA));
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
                entry.prompt(), entry.response(), entry.tokens(), entry.latency(), entry.fallback(),
                entry.subjectId(), entry.replay().map(ReplayRequest::key)));
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

    /**
     * One LLM call. {@code response} is empty when the call failed; {@code fallback} when its fallback was used;
     * {@code subjectId} is the object the call was made for (e.g. the news item id). {@code replay} is the same
     * {@link ReplayRequest} the call was made with, given only when the answer was usable: the row can then be
     * replayed under exactly the key the lookup computes (ADR 0015).
     */
    public record GenerationEntry(String feature, String model, String promptVersion, String prompt,
                                  Optional<String> response, TokenCount tokens, Duration latency,
                                  boolean fallback, Optional<UUID> subjectId, Optional<ReplayRequest> replay) {}

    /**
     * What identifies a recorded answer (L7, ADR 0015): feature, model, prompt version and the variable input (the data
     * block, not the system prompt). Same parts as {@link GenerationEntry}, so a recorded row is found again.
     */
    public record ReplayRequest(String feature, String model, String promptVersion, String input) {

        ReplayKey key() {
            return ReplayKey.of(feature, model, promptVersion, input);
        }
    }

    public record BudgetView(String feature, LocalDate date, BigDecimal spentUsd, BigDecimal limitUsd,
                             BigDecimal remainingUsd, boolean exhausted) {}

    public record FeatureCostView(String feature, int calls, long promptTokens, long completionTokens,
                                  BigDecimal costUsd) {}

    public record CostSummaryView(LocalDate from, LocalDate to, List<FeatureCostView> byFeature, int totalCalls,
                                  BigDecimal totalUsd) {}
}
