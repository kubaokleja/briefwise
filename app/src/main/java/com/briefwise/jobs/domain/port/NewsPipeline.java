package com.briefwise.jobs.domain.port;

import com.briefwise.jobs.domain.model.EvalResult;
import com.briefwise.jobs.domain.model.PublishResult;
import java.time.Instant;
import java.util.Map;

/** Outbound port: the {@code news} steps a job calls (through {@code NewsFacade}). Results are reported as is. */
public interface NewsPipeline {

    Map<String, Object> ingest();

    PublishResult publishPending();

    /** @throws IllegalStateException if the broker cannot be asked */
    long consumerLag();

    /** Counts per outcome, {@code stoppedOnDeferral} and {@code stoppedOnDeadline}. */
    Map<String, Object> classifyPending(Instant deadline);

    /** Counts per outcome, {@code stoppedOnDeferral} and {@code stoppedOnDeadline}. */
    Map<String, Object> summarizePortfolios(Instant deadline);

    /** @throws IllegalStateException if the golden set is missing or malformed */
    EvalResult evaluateClassification();
}
