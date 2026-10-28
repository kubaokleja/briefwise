package com.briefwise.news.infrastructure.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.news.summary.*} (ADR 0017). {@code feature} keys the budget and the generation log.
 * {@code max-tokens} covers adaptive thinking plus up to 5 points of JSON. {@code effort} applies to Anthropic only.
 * {@code max-attempts}: the first call plus re-asks after invalid output (the compliance re-ask is a separate call
 * by the service). {@code max-summary-chars}: each item's summary is cut to this in the prompt. The model is the
 * chat model's ({@code spring.ai.<provider>.chat.model}).
 */
@Validated
@ConfigurationProperties("briefwise.news.summary")
record NewsSummaryProperties(@DefaultValue("news-summary") @NotBlank String feature,
                             @DefaultValue("2048") @Positive int maxTokens,
                             @DefaultValue("low") @NotBlank String effort,
                             @DefaultValue("2") @Positive @Max(3) int maxAttempts,
                             @DefaultValue("5") @Positive int maxPoints,
                             @DefaultValue("300") @Positive int maxSummaryChars) {
}
