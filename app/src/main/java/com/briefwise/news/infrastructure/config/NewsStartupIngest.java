package com.briefwise.news.infrastructure.config;

import com.briefwise.news.NewsFacade;
import com.briefwise.news.NewsFacade.IngestSummaryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One ingest run at startup when {@code briefwise.news.ingest.on-startup=true}; off by default. A stopgap trigger
 * for local runs (e.g. the headline export for labelling) until the admin endpoint and job runner (M1 slice 6).
 * A failure is logged, never fatal.
 */
@Component
@ConditionalOnProperty(prefix = "briefwise.news.ingest", name = "on-startup", havingValue = "true")
class NewsStartupIngest implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NewsStartupIngest.class);

    private final NewsFacade news;

    NewsStartupIngest(NewsFacade news) {
        this.news = news;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            IngestSummaryView summary = news.ingest();
            log.info("Startup news ingest: {} new, {} near-duplicates, {} already known; failed tickers {}",
                    summary.inserted(), summary.nearDuplicates(), summary.alreadyKnown(), summary.failed());
        } catch (RuntimeException e) {
            log.warn("Startup news ingest failed: {}", e.getMessage());
        }
    }
}
