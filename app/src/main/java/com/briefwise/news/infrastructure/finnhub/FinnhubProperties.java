package com.briefwise.news.infrastructure.finnhub;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Finnhub company-news client. {@code api-key} comes from {@code FINNHUB_API_KEY} ({@code .env} locally, GitHub
 * Secrets in CI); it may be empty so the app starts without it, and ingest then fails with a clear message.
 * Retries cover 429, 5xx and I/O errors (ADR 0005): {@code max-retries} after the first attempt, delay doubling.
 */
@Validated
@ConfigurationProperties("briefwise.news.finnhub")
record FinnhubProperties(@DefaultValue("https://finnhub.io/api/v1") @NotBlank String baseUrl,
                         @DefaultValue("") String apiKey,
                         @DefaultValue("5s") @NotNull Duration connectTimeout,
                         @DefaultValue("10s") @NotNull Duration readTimeout,
                         @DefaultValue("2") @PositiveOrZero int maxRetries,
                         @DefaultValue("1s") @NotNull Duration retryDelay) {
}
