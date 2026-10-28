package com.briefwise.jobs.infrastructure.config;

import com.briefwise.jobs.domain.model.RunPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.job-runner.*} into the run policy of the job service. */
@Configuration(proxyBeanMethods = false)
class JobsConfig {

    @Bean
    RunPolicy runPolicy(JobRunnerProperties properties) {
        return new RunPolicy(properties.maxDuration(), properties.lagPollInterval());
    }
}
