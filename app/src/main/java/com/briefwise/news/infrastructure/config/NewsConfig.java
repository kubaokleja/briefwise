package com.briefwise.news.infrastructure.config;

import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.IngestPolicy;
import com.briefwise.news.domain.model.RelayPolicy;
import com.briefwise.news.domain.model.SelectionPolicy;
import com.briefwise.news.domain.model.SweepPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Turns {@code briefwise.news.*} into the domain policies the ingest, relay, sweep and selection services use. */
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

    @Bean
    SweepPolicy sweepPolicy(NewsProperties properties) {
        NewsProperties.Sweep sweep = properties.sweep();
        return new SweepPolicy(sweep.minAge(), sweep.batchSize(), sweep.maxAttempts(),
                sweep.providerErrorRetryWindow());
    }

    @Bean
    SelectionPolicy selectionPolicy(NewsProperties properties) {
        NewsProperties.Selection selection = properties.selection();
        return new SelectionPolicy(selection.window(), selection.maxItems(), selection.minRelevance());
    }
}
