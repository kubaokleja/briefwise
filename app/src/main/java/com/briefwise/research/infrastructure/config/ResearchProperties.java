package com.briefwise.research.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.research.*}. {@code ingest.lookback-days}: 8-Ks filed within this many days are ingested, and
 * 10-Qs too when a company has no 10-K yet (ADR 0025: the last 12 months).
 */
@Validated
@ConfigurationProperties("briefwise.research")
record ResearchProperties(@Valid @DefaultValue Ingest ingest) {

    record Ingest(@DefaultValue("365") @Positive int lookbackDays) {}
}
