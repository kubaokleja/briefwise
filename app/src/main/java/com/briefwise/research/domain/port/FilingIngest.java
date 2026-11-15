package com.briefwise.research.domain.port;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.FilingIngestSummary;
import com.briefwise.research.domain.model.IngestedFiling;
import com.briefwise.shared.domain.model.Ticker;

/**
 * Inbound port: fetch filings, screen every section and store them (ADR 0025, 0026). Implemented by a
 * package-private service. Safe to repeat: a filing with the same accession and content hash is left as stored.
 */
public interface FilingIngest {

    /**
     * One filing, whether or not the selection policy would pick it.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown, or the
     *         source does not list the accession for the company ({@link
     *         com.briefwise.research.domain.model.FilingNotFoundException})
     * @throws IllegalArgumentException if the instrument has no filings on EDGAR (a fund, or no CIK)
     * @throws com.briefwise.research.domain.model.FilingSourceUnavailableException if the source fails after retries
     * @throws IllegalStateException if the source is misconfigured or a snapshot file was edited
     */
    IngestedFiling ingestFiling(Ticker ticker, AccessionNo accessionNo);

    /**
     * The filings the selection policy picks for one company. A filing that fails is reported in
     * {@link FilingIngestSummary#failed()} and the next one is ingested; a fund is reported as skipped.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown
     * @throws IllegalStateException if the source is misconfigured or a snapshot file was edited
     */
    FilingIngestSummary ingestFilings(Ticker ticker);

    /**
     * {@link #ingestFilings} for every held company. A company whose listing fails is reported in
     * {@link FilingIngestSummary#failed()} without an accession.
     *
     * @throws IllegalStateException if the source is misconfigured or a snapshot file was edited
     */
    FilingIngestSummary ingestHeld();
}
