package com.briefwise.aiops.infrastructure.ai;

import com.briefwise.aiops.domain.model.ReplayPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.llm.*} into the domain policy the replay service uses. */
@Configuration(proxyBeanMethods = false)
class ReplayConfig {

    @Bean
    ReplayPolicy replayPolicy(LlmProperties properties) {
        return properties.policy();
    }
}
