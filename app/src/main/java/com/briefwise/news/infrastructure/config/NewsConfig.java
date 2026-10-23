package com.briefwise.news.infrastructure.config;

import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.IngestPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.news.*} into the domain policy the ingest service uses. */
@Configuration(proxyBeanMethods = false)
class NewsConfig {

    @Bean
    IngestPolicy ingestPolicy(NewsProperties properties) {
        return new IngestPolicy(properties.ingest().lookbackDays(),
                new DedupPolicy(properties.dedup().headlineSimilarity(), properties.dedup().window()));
    }
}
