package com.briefwise.news.infrastructure.config;

import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.IngestPolicy;
import com.briefwise.news.domain.model.RelayPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.news.*} into the domain policies the ingest and relay services use. */
@Configuration(proxyBeanMethods = false)
class NewsConfig {

    @Bean
    IngestPolicy ingestPolicy(NewsProperties properties) {
        return new IngestPolicy(properties.ingest().lookbackDays(),
                new DedupPolicy(properties.dedup().headlineSimilarity(), properties.dedup().window()));
    }

    @Bean
    RelayPolicy relayPolicy(NewsProperties properties) {
        return new RelayPolicy(properties.outbox().batchSize());
    }
}
