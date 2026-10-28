package com.briefwise.jobs.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.job-runner.*} (ADR 0016). {@code max-duration}: a run stops waiting after this long (the runner
 * then exits 1). {@code lag-poll-interval}: pause between consumer lag checks. Which job the runner starts comes from
 * {@code --briefwise.job}; whether a job may run from {@code briefwise.jobs.<id>.enabled}.
 */
@Validated
@ConfigurationProperties("briefwise.job-runner")
record JobRunnerProperties(@DefaultValue("15m") @NotNull Duration maxDuration,
                           @DefaultValue("2s") @NotNull Duration lagPollInterval) {}
