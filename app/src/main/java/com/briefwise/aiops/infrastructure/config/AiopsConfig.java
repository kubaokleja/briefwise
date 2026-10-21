package com.briefwise.aiops.infrastructure.config;

import com.briefwise.aiops.domain.model.DailyBudgets;
import com.briefwise.aiops.domain.model.ModelPrice;
import com.briefwise.aiops.domain.model.PriceList;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.aiops.*} into the domain objects the application services use. */
@Configuration(proxyBeanMethods = false)
class AiopsConfig {

    @Bean
    PriceList priceList(AiopsProperties properties) {
        Map<String, ModelPrice> prices = properties.pricing().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new ModelPrice(e.getValue().input(), e.getValue().output())));
        return new PriceList(prices);
    }

    @Bean
    DailyBudgets dailyBudgets(AiopsProperties properties) {
        return new DailyBudgets(properties.dailyBudgetUsd());
    }
}
