package com.briefwise.research.infrastructure.edgar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * SEC EDGAR client (ADR 0026). Submissions come from {@code data-base-url}, documents from {@code archive-base-url}.
 * {@code user-agent} comes from {@code SEC_USER_AGENT} ("Name contact@email", SEC fair-access rule). It may be empty
 * so the app starts without it; a call then fails before any request. Retries cover 429, 5xx and I/O errors
 * (ADR 0005); {@code min-request-gap} keeps the request rate below the SEC limit of 10 per second.
 */
@Validated
@ConfigurationProperties("briefwise.research.edgar")
record EdgarProperties(@DefaultValue("https://data.sec.gov") @NotBlank String dataBaseUrl,
                       @DefaultValue("https://www.sec.gov") @NotBlank String archiveBaseUrl,
                       @DefaultValue("") String userAgent,
                       @DefaultValue("5s") @NotNull Duration connectTimeout,
                       @DefaultValue("30s") @NotNull Duration readTimeout,
                       @DefaultValue("2") @PositiveOrZero int maxRetries,
                       @DefaultValue("1s") @NotNull Duration retryDelay,
                       @DefaultValue("150ms") @NotNull Duration minRequestGap) {
}
