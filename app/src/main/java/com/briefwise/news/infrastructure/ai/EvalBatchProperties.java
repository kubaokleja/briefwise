package com.briefwise.news.infrastructure.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.news.eval.batch.*} (ADR 0018). {@code feature} keys the eval's own budget and generation log
 * entries, apart from production classification. A Message Batch usually ends within minutes (at most 24 h):
 * the adapter polls every {@code poll-interval}; after {@code max-wait} it cancels the batch (unfinished requests are
 * then not billed) and waits up to {@code cancel-wait} for it to end, so the requests that did finish, and are billed,
 * still reach the generation log and the budget. {@code request-timeout} is per HTTP call (creating a batch of 258 requests is one
 * large POST). Model, max tokens and effort are production's ({@code briefwise.news.classification.*}).
 */
@Validated
@ConfigurationProperties("briefwise.news.eval.batch")
record EvalBatchProperties(@DefaultValue("news-classification-eval") @NotBlank String feature,
                           @DefaultValue("30s") @NotNull Duration pollInterval,
                           @DefaultValue("60m") @NotNull Duration maxWait,
                           @DefaultValue("5m") @NotNull Duration cancelWait,
                           @DefaultValue("60s") @NotNull Duration requestTimeout) {
}
