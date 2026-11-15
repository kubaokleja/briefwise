package com.briefwise.jobs.domain.port;

import java.util.Map;

/** Outbound port: the research context's filing ingest, as the {@code filing-ingest} job runs it. */
public interface FilingPipeline {

    /**
     * Ingests the filings of every held company. Counts per outcome, {@code attempted} (filings plus company listings
     * tried) and {@code failed} (how many of them failed).
     *
     * @throws IllegalStateException if the filing source is misconfigured or a snapshot file was edited
     */
    Map<String, Object> ingestHeld();
}
