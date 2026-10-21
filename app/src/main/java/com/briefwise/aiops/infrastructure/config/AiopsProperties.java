package com.briefwise.aiops.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Model prices (USD per million tokens, keyed by model name) and daily budgets (USD, keyed by feature).
 * Model names live here and nowhere in code. A model name containing dots or colons (Ollama tags such as
 * {@code llama3.2:3b}) needs the bracket form in YAML: {@code "[llama3.2:3b]"}.
 */
@Validated
@ConfigurationProperties("briefwise.aiops")
record AiopsProperties(Map<String, @Valid Price> pricing, Map<String, @NotNull @PositiveOrZero BigDecimal> dailyBudgetUsd) {

    AiopsProperties {
        pricing = pricing == null ? Map.of() : Map.copyOf(pricing);
        dailyBudgetUsd = dailyBudgetUsd == null ? Map.of() : Map.copyOf(dailyBudgetUsd);
    }

    record Price(@NotNull @PositiveOrZero BigDecimal input, @NotNull @PositiveOrZero BigDecimal output) {}
}
