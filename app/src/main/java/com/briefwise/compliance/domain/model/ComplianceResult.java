package com.briefwise.compliance.domain.model;

import java.util.List;
import java.util.Objects;

/** Outcome of checking a generated text against the compliance rules (DESIGN 11). */
public record ComplianceResult(List<Violation> violations) {

    public ComplianceResult {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
    }

    public boolean passed() {
        return violations.isEmpty();
    }
}
