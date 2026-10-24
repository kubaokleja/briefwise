package com.briefwise.news.infrastructure.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.news.kafka.*} (ADR 0011). {@code partitions}: for {@code news.raw} and its DLT (same count, so a
 * dead letter keeps its partition). {@code max-attempts} deliveries {@code retry-backoff} apart, then the DLT.
 * {@code send-timeout}: how long the relay waits for one broker ack. {@code consumer-enabled}: start the listener.
 */
@Validated
@ConfigurationProperties("briefwise.news.kafka")
record NewsKafkaProperties(@DefaultValue("3") @Min(1) int partitions,
                           @DefaultValue("3") @Min(1) int maxAttempts,
                           @DefaultValue("1s") @NotNull Duration retryBackoff,
                           @DefaultValue("15s") @NotNull Duration sendTimeout,
                           @DefaultValue("true") boolean consumerEnabled) {}
