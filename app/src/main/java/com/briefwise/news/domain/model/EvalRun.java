package com.briefwise.news.domain.model;

import com.briefwise.news.domain.model.RegressionGate.GateResult;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of one eval run. Only {@code PASSED} is a success: an eval that measured nothing (refused, incomplete) must
 * not let a prompt change through as if it had been checked.
 */
public record EvalRun(Status status, Optional<EvalReport> report, Optional<GateResult> gate, String message) {

    public enum Status { PASSED, REGRESSED, REFUSED, INCOMPLETE }

    public EvalRun {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(message, "message");
    }

    public boolean passed() {
        return status == Status.PASSED;
    }
}
