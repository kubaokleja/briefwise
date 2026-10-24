package com.briefwise.news.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.news.*}. {@code ingest.lookback-days}: each run asks for today and that many UTC days before
 * (overlap is absorbed by the URL hash). {@code dedup}: the near-duplicate rule (D1, ADR 0009). {@code outbox}: the
 * polling relay (ADR 0011); {@code relay-enabled} and {@code poll-interval} are read by the relay scheduler.
 */
@Validated
@ConfigurationProperties("briefwise.news")
record NewsProperties(@Valid @DefaultValue Ingest ingest, @Valid @DefaultValue Dedup dedup,
                      @Valid @DefaultValue Outbox outbox) {

    record Ingest(@DefaultValue("2") @PositiveOrZero int lookbackDays,
                  @DefaultValue("false") boolean onStartup) {}

    record Dedup(@DefaultValue("0.6") @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
                 double headlineSimilarity,
                 @DefaultValue("48h") @NotNull Duration window) {}

    record Outbox(@DefaultValue("true") boolean relayEnabled,
                  @DefaultValue("5s") @NotNull Duration pollInterval,
                  @DefaultValue("100") @Positive int batchSize) {}
}
