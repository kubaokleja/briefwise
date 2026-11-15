package com.briefwise.research.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.List;

/**
 * One ingest run: the companies asked for, those without EDGAR filings (funds, no CIK), how many filings the
 * selection policy picked, what happened to each, and what failed.
 */
public record FilingIngestSummary(List<Ticker> companies, List<Ticker> skipped, int selected,
                                  List<IngestedFiling> ingested, List<FailedFiling> failed) {

    public FilingIngestSummary {
        companies = List.copyOf(companies);
        skipped = List.copyOf(skipped);
        ingested = List.copyOf(ingested);
        failed = List.copyOf(failed);
    }

    public long count(IngestOutcome outcome) {
        return ingested.stream().filter(f -> f.outcome() == outcome).count();
    }

    public int sections() {
        return ingested.stream().mapToInt(IngestedFiling::sections).sum();
    }

    public int suspectedSections() {
        return ingested.stream().mapToInt(IngestedFiling::suspectedSections).sum();
    }
}
