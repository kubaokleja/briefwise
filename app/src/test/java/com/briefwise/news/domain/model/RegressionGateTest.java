package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.news.domain.model.RegressionGate.GateResult;
import com.briefwise.news.domain.model.RegressionGate.Verdict;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegressionGateTest {

    private static final String GOLDEN = "a".repeat(64);
    private static final String OTHER_GOLDEN = "b".repeat(64);

    private final RegressionGate gate = new RegressionGate(3.0);

    private static EvalBaseline run(double f1) {
        return new EvalBaseline("claude-haiku-5-5", "v1", 258, Optional.of(GOLDEN), f1);
    }

    private static Optional<EvalBaseline> baseline(double f1) {
        return Optional.of(run(f1));
    }

    @Test
    void without_a_baseline_the_first_run_passes_and_says_so() {
        GateResult result = gate.check(run(0.42), Optional.empty());

        assertThat(result.verdict()).isEqualTo(Verdict.NO_BASELINE);
        assertThat(result.passed()).isTrue();
        assertThat(result.message()).contains("no baseline");
    }

    @Test
    void a_drop_within_three_points_passes() {
        assertThat(gate.check(run(0.771), baseline(0.800)).verdict()).isEqualTo(Verdict.PASSED);
        assertThat(gate.check(run(0.770), baseline(0.800)).passed()).isTrue();
    }

    @Test
    void a_drop_of_more_than_three_points_fails() {
        GateResult result = gate.check(run(0.769), baseline(0.800));

        assertThat(result.verdict()).isEqualTo(Verdict.REGRESSED);
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("0.769").contains("0.800").contains("-3.1 pp");
    }

    @Test
    void an_improvement_passes() {
        assertThat(gate.check(run(0.85), baseline(0.80)).passed()).isTrue();
    }

    @Test
    void a_new_prompt_version_or_model_is_compared_and_named() {
        EvalBaseline v2 = new EvalBaseline("claude-sonnet-5-5", "v2", 258, Optional.of(GOLDEN), 0.70);

        GateResult result = gate.check(v2, baseline(0.80));

        assertThat(result.verdict()).isEqualTo(Verdict.REGRESSED);
        assertThat(result.message()).contains("prompt v2 vs baseline v1")
                .contains("model claude-sonnet-5-5 vs baseline claude-haiku-5-5");
    }

    @Test
    void a_baseline_on_a_different_number_of_cases_is_not_comparable() {
        EvalBaseline grown = new EvalBaseline("claude-haiku-5-5", "v1", 270, Optional.of(GOLDEN), 0.90);

        GateResult result = gate.check(grown, baseline(0.80));

        assertThat(result.verdict()).isEqualTo(Verdict.NOT_COMPARABLE);
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("270 cases, baseline 258").contains("not gated")
                .contains("accept-new-baseline");
    }

    @Test
    void a_baseline_on_a_relabelled_golden_set_is_not_comparable_even_with_a_better_score() {
        EvalBaseline relabelled = new EvalBaseline("claude-haiku-5-5", "v1", 258, Optional.of(OTHER_GOLDEN), 0.95);

        GateResult result = gate.check(relabelled, baseline(0.80));

        assertThat(result.verdict()).isEqualTo(Verdict.NOT_COMPARABLE);
        assertThat(result.message()).contains("hash bbbbbbbbbbbb vs baseline aaaaaaaaaaaa");
    }

    @Test
    void a_baseline_without_a_golden_hash_is_not_comparable() {
        Optional<EvalBaseline> legacy = Optional.of(
                new EvalBaseline("claude-haiku-5-5", "v1", 258, Optional.empty(), 0.80));

        GateResult result = gate.check(run(0.80), legacy);

        assertThat(result.verdict()).isEqualTo(Verdict.NOT_COMPARABLE);
        assertThat(result.message()).contains("baseline has no golden set hash");
    }

    @Test
    void accepting_a_new_baseline_passes_a_run_on_a_changed_golden_set() {
        RegressionGate accepting = new RegressionGate(3.0, true);
        EvalBaseline relabelled = new EvalBaseline("claude-haiku-5-5", "v1", 258, Optional.of(OTHER_GOLDEN), 0.60);

        GateResult result = accepting.check(relabelled, baseline(0.80));

        assertThat(result.verdict()).isEqualTo(Verdict.BASELINE_ACCEPTED);
        assertThat(result.passed()).isTrue();
        assertThat(result.message()).contains("commit latest.json as baseline.json");
    }

    @Test
    void accepting_a_new_baseline_cannot_hide_a_regression_on_the_same_golden_set() {
        RegressionGate accepting = new RegressionGate(3.0, true);

        assertThat(accepting.check(run(0.70), baseline(0.80)).verdict()).isEqualTo(Verdict.REGRESSED);
    }

    @Test
    void a_negative_limit_is_rejected() {
        assertThatThrownBy(() -> new RegressionGate(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
