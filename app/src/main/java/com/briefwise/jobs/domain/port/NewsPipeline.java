package com.briefwise.jobs.domain.port;

import com.briefwise.jobs.domain.model.PublishResult;
import java.util.Map;

/** Outbound port: the {@code news} steps a job calls (through {@code NewsFacade}). Results are reported as is. */
public interface NewsPipeline {

    Map<String, Object> ingest();

    PublishResult publishPending();

    /** @throws IllegalStateException if the broker cannot be asked */
    long consumerLag();

    Map<String, Object> classifyPending();

    /** Counts per outcome and {@code stoppedOnDeferral}. */
    Map<String, Object> summarizePortfolios();
}
