package com.briefwise.research.domain.model;

import java.util.List;

/** Result of the injection screen on one section (ADR 0013). A suspected section is stored but never retrieved. */
public record Screening(boolean suspected, List<String> matchedRules) {

    public static final Screening CLEAN = new Screening(false, List.of());

    public Screening {
        matchedRules = List.copyOf(matchedRules);
        if (suspected == matchedRules.isEmpty()) {
            throw new IllegalArgumentException("suspected must be true exactly when rules matched");
        }
    }
}
