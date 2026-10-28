package com.briefwise.news.infrastructure.eval;

import com.briefwise.news.domain.model.RegressionGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.news.eval.*} into the eval's regression gate (ADR 0018). */
@Configuration(proxyBeanMethods = false)
class NewsEvalConfig {

    @Bean
    RegressionGate regressionGate(NewsEvalProperties properties) {
        return new RegressionGate(properties.maxDropPoints());
    }
}
