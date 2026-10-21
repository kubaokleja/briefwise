package com.briefwise.shared.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One injectable clock, so services that depend on "today" can be tested with a fixed one. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
