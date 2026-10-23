package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.List;

/**
 * Result of one ingest run. {@code inserted} new items to classify, {@code nearDuplicates} stored as
 * {@code SKIPPED}, {@code alreadyKnown} URLs seen before (only the ticker was added). {@code skipped} tickers were
 * not queried (funds); {@code failed} tickers could not be fetched and are retried on the next run.
 */
public record IngestSummary(List<Ticker> queried, List<Ticker> skipped, List<Ticker> failed, int fetched,
                            int inserted, int nearDuplicates, int alreadyKnown) {

    public IngestSummary {
        queried = List.copyOf(queried);
        skipped = List.copyOf(skipped);
        failed = List.copyOf(failed);
    }
}
