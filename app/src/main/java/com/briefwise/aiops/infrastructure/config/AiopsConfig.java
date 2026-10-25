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
                        e -> toModelPrice(e.getValue())));
        return new PriceList(prices);
    }

    private static ModelPrice toModelPrice(AiopsProperties.Price p) {
        return new ModelPrice(p.input(), p.output(),
                p.cacheWrite() != null ? p.cacheWrite() : ModelPrice.standardCacheWrite(p.input()),
                p.cacheRead() != null ? p.cacheRead() : ModelPrice.standardCacheRead(p.input()));
    }

    @Bean
    DailyBudgets dailyBudgets(AiopsProperties properties) {
        return new DailyBudgets(properties.dailyBudgetUsd());
    }
}
