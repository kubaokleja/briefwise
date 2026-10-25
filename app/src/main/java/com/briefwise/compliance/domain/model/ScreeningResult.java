package com.briefwise.compliance.domain.model;

import java.util.List;
import java.util.Objects;

/** Outcome of screening untrusted text before it goes into a prompt (ADR 0013). */
public record ScreeningResult(List<String> matchedRules) {

    public ScreeningResult {
        matchedRules = List.copyOf(Objects.requireNonNull(matchedRules, "matchedRules"));
    }

    /** True if any rule matched: the caller must not send the text to an LLM. */
    public boolean suspected() {
        return !matchedRules.isEmpty();
    }
}
