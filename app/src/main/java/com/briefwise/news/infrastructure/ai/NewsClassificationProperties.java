package com.briefwise.news.infrastructure.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.news.classification.*} (ADR 0012). {@code feature} keys the budget and the generation log.
 * {@code max-tokens} covers adaptive thinking plus about 100 tokens of JSON. {@code effort} applies to Anthropic only
 * (Haiku 5.5 takes no temperature). {@code max-attempts}: the first call plus re-asks after invalid output; provider
 * errors are retried by the SDK ({@code spring.ai.anthropic.max-retries}), not here. The model name is the chat
 * model's ({@code spring.ai.<provider>.chat.model}).
 */
@Validated
@ConfigurationProperties("briefwise.news.classification")
record NewsClassificationProperties(@DefaultValue("news-classification") @NotBlank String feature,
                                    @DefaultValue("1024") @Positive int maxTokens,
                                    @DefaultValue("low") @NotBlank String effort,
                                    @DefaultValue("2") @Positive @Max(3) int maxAttempts,
                                    @DefaultValue("2000") @Positive int maxSummaryChars) {
}
