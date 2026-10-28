package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegressionGateTest {

    private final RegressionGate gate = new RegressionGate(3.0);

    private static Optional<EvalBaseline> baseline(double f1) {
        return Optional.of(new EvalBaseline("claude-haiku-5-5", "v1", f1));
    }

    @Test
    void without_a_baseline_the_first_run_passes_and_says_so() {
        RegressionGate.GateResult result = gate.check(0.42, Optional.empty());

        assertThat(result.passed()).isTrue();
        assertThat(result.message()).contains("no baseline");
    }

    @Test
    void a_drop_within_three_points_passes() {
        assertThat(gate.check(0.771, baseline(0.800)).passed()).isTrue();
        assertThat(gate.check(0.770, baseline(0.800)).passed()).isTrue();
    }

    @Test
    void a_drop_of_more_than_three_points_fails() {
        RegressionGate.GateResult result = gate.check(0.769, baseline(0.800));

        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("0.769").contains("0.800").contains("-3.1 pp");
    }

    @Test
    void an_improvement_passes() {
        assertThat(gate.check(0.85, baseline(0.80)).passed()).isTrue();
    }

    @Test
    void a_negative_limit_is_rejected() {
        assertThatThrownBy(() -> new RegressionGate(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
