package com.briefwise.news.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The eval's merge gate (DESIGN 10, ADR 0018, ADR 0022): sentiment macro F1 may fall at most {@code maxDropPoints}
 * percentage points below the committed baseline. A relative gate, not an absolute threshold: it catches a prompt
 * change that makes things worse without having to agree on what "good enough" is. Without a baseline (first run) it
 * passes and says so.
 *
 * <p>A relative gate needs a fixed ruler: the baseline must have been measured on the same golden set (case count
 * and {@link GoldenSetFingerprint}). Otherwise the run is {@code NOT_COMPARABLE} and fails, unless
 * {@code acceptNewBaseline} is set, which makes it {@code BASELINE_ACCEPTED} (the maintainer then commits it as the
 * new baseline). The flag only applies to a run that is not comparable: it cannot hide a regression. A different
 * model or prompt version is what a run measures, so it is compared and named in the message.
 */
public record RegressionGate(double maxDropPoints, boolean acceptNewBaseline) {

    public enum Verdict { NO_BASELINE, PASSED, REGRESSED, NOT_COMPARABLE, BASELINE_ACCEPTED }

    public RegressionGate {
        if (maxDropPoints < 0) {
            throw new IllegalArgumentException("maxDropPoints must not be negative");
        }
    }

    public RegressionGate(double maxDropPoints) {
        this(maxDropPoints, false);
    }

    /** @param run the current run in baseline shape ({@link EvalReport#asBaseline()}) */
    public GateResult check(EvalBaseline run, Optional<EvalBaseline> baseline) {
        Objects.requireNonNull(run, "run");
        if (baseline.isEmpty()) {
            return new GateResult(Verdict.NO_BASELINE, run.sentimentMacroF1(), Optional.empty(),
                    "no baseline yet; commit this run as baseline.json to start gating");
        }
        EvalBaseline base = baseline.get();
        double dropPoints = (base.sentimentMacroF1() - run.sentimentMacroF1()) * 100;
        String scores = String.format(Locale.ROOT, "sentiment macro F1 %.3f vs baseline %.3f (%+.1f pp",
                run.sentimentMacroF1(), base.sentimentMacroF1(), -dropPoints);

        List<String> differences = goldenSetDifferences(run, base);
        if (!differences.isEmpty()) {
            String why = String.join("; ", differences);
            if (acceptNewBaseline) {
                return new GateResult(Verdict.BASELINE_ACCEPTED, run.sentimentMacroF1(), baseline,
                        "new baseline accepted (" + why + "); " + scores
                                + ", not gated); commit latest.json as baseline.json");
            }
            return new GateResult(Verdict.NOT_COMPARABLE, run.sentimentMacroF1(), baseline,
                    "not comparable with the baseline (" + why + "); " + scores
                            + ", not gated); if the golden set change is intended, rerun with "
                            + "accept-new-baseline and commit latest.json as baseline.json");
        }

        boolean passed = dropPoints <= maxDropPoints + 1e-9;
        String message = scores + String.format(Locale.ROOT, ", limit -%.1f pp)", maxDropPoints)
                + measuredChanges(run, base);
        return new GateResult(passed ? Verdict.PASSED : Verdict.REGRESSED, run.sentimentMacroF1(), baseline, message);
    }

    private static List<String> goldenSetDifferences(EvalBaseline run, EvalBaseline base) {
        List<String> differences = new ArrayList<>();
        if (run.cases() != base.cases()) {
            differences.add("golden set has " + run.cases() + " cases, baseline " + base.cases());
        }
        if (base.goldenSha256().isEmpty()) {
            differences.add("baseline has no golden set hash");
        } else if (!base.goldenSha256().equals(run.goldenSha256())) {
            differences.add("golden set hash " + shortHash(run.goldenSha256()) + " vs baseline "
                    + shortHash(base.goldenSha256()));
        }
        return differences;
    }

    private static String measuredChanges(EvalBaseline run, EvalBaseline base) {
        List<String> changes = new ArrayList<>();
        if (!run.promptVersion().equals(base.promptVersion())) {
            changes.add("prompt " + run.promptVersion() + " vs baseline " + base.promptVersion());
        }
        if (!run.model().equals(base.model())) {
            changes.add("model " + run.model() + " vs baseline " + base.model());
        }
        return changes.isEmpty() ? "" : "; " + String.join(", ", changes);
    }

    private static String shortHash(Optional<String> sha256) {
        return sha256.map(h -> h.substring(0, Math.min(12, h.length()))).orElse("(none)");
    }

    public record GateResult(Verdict verdict, double sentimentMacroF1, Optional<EvalBaseline> baseline,
                             String message) {

        public GateResult {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(baseline, "baseline");
            Objects.requireNonNull(message, "message");
        }

        /** Only a gated pass, a first run or a deliberately accepted new baseline lets the change through. */
        public boolean passed() {
            return verdict == Verdict.PASSED || verdict == Verdict.NO_BASELINE
                    || verdict == Verdict.BASELINE_ACCEPTED;
        }
    }
}
