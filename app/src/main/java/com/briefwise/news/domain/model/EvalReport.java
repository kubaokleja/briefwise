package com.briefwise.news.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Result of one classification eval run (DESIGN 10, ADR 0018): scores on all golden rows and on the random rows alone,
 * how many answers were unusable, and the cost of the run (batch price). Holds metrics only, never news text, so it
 * may be committed and posted on a PR.
 */
public record EvalReport(String model, String promptVersion, String batchId, Instant ranAt, int cases, int randomCases,
                         int invalid, BigDecimal costUsd, EvalScores all, EvalScores random) {

    public EvalReport {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(ranAt, "ranAt");
        Objects.requireNonNull(costUsd, "costUsd");
        Objects.requireNonNull(all, "all");
        Objects.requireNonNull(random, "random");
    }

    public static EvalReport of(List<GoldenCase> cases, BatchOutcome.Completed batch, Instant ranAt) {
        Map<UUID, EvalPrediction> predictions = batch.predictions().stream()
                .collect(Collectors.toMap(EvalPrediction::caseId, Function.identity(), (first, second) -> first));
        List<GoldenCase> randomCases = cases.stream().filter(GoldenCase::random).toList();
        int invalid = (int) cases.stream()
                .filter(c -> !predictions.containsKey(c.id()) || !predictions.get(c.id()).valid())
                .count();
        return new EvalReport(batch.model(), batch.promptVersion(), batch.batchId(), ranAt, cases.size(),
                randomCases.size(), invalid, batch.costUsd(), EvalScores.of(cases, predictions),
                EvalScores.of(randomCases, predictions));
    }

    /** What 1,000 items would cost at this run's average, the unit DESIGN 8 compares models by. */
    public BigDecimal costPerThousandUsd() {
        return cases == 0 ? BigDecimal.ZERO
                : costUsd.multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(cases), 4, RoundingMode.HALF_UP);
    }

    public double invalidRate() {
        return cases == 0 ? 0 : (double) invalid / cases;
    }
}
