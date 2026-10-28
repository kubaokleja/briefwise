package com.briefwise.news.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The eval's merge gate (DESIGN 10, ADR 0018): sentiment macro F1 may fall at most {@code maxDropPoints} percentage
 * points below the committed baseline. A relative gate, not an absolute threshold: it catches a prompt change that
 * makes things worse without having to agree on what "good enough" is. Without a baseline (first run) it passes and
 * says so.
 */
public record RegressionGate(double maxDropPoints) {

    public RegressionGate {
        if (maxDropPoints < 0) {
            throw new IllegalArgumentException("maxDropPoints must not be negative");
        }
    }

    public GateResult check(double sentimentMacroF1, Optional<EvalBaseline> baseline) {
        if (baseline.isEmpty()) {
            return new GateResult(true, sentimentMacroF1, Optional.empty(),
                    "no baseline yet; commit this run as baseline.json to start gating");
        }
        double dropPoints = (baseline.get().sentimentMacroF1() - sentimentMacroF1) * 100;
        boolean passed = dropPoints <= maxDropPoints + 1e-9;
        String message = String.format(Locale.ROOT,
                "sentiment macro F1 %.3f vs baseline %.3f (%+.1f pp, limit -%.1f pp)",
                sentimentMacroF1, baseline.get().sentimentMacroF1(), -dropPoints, maxDropPoints);
        return new GateResult(passed, sentimentMacroF1, baseline, message);
    }

    public record GateResult(boolean passed, double sentimentMacroF1, Optional<EvalBaseline> baseline,
                             String message) {}
}
