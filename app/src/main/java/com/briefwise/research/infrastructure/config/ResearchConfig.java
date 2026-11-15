package com.briefwise.research.infrastructure.config;

import com.briefwise.research.domain.model.FilingSelectionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.research.*} into the domain policies of the research services. */
@Configuration(proxyBeanMethods = false)
class ResearchConfig {

    @Bean
    FilingSelectionPolicy filingSelectionPolicy(ResearchProperties properties) {
        return new FilingSelectionPolicy(properties.ingest().lookbackDays());
    }
}
