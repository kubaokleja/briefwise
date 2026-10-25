package com.briefwise.compliance.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Untrusted content matched an injection rule and was kept away from the LLM (DESIGN 6.4, ADR 0013). Published
 * in-process; no listener yet (the M6 admin view will show them). {@code source} names the kind of content, e.g.
 * {@code news}; {@code rules} the rules that matched.
 */
public record InjectionSuspected(UUID contentId, String source, List<String> rules, Instant occurredAt) {

    public InjectionSuspected {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(source, "source");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
