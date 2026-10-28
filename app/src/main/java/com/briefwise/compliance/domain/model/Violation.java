package com.briefwise.compliance.domain.model;

import java.util.Objects;

/**
 * One broken rule. {@code offendingText} is the matched phrase (or the passage without a citation), so a re-ask can
 * name exactly what to rewrite.
 */
public record Violation(RuleId rule, String offendingText, String explanation) {

    public Violation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(offendingText, "offendingText");
        Objects.requireNonNull(explanation, "explanation");
    }
}
