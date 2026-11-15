package com.briefwise.research;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.FailedFiling;
import com.briefwise.research.domain.model.FilingIngestSummary;
import com.briefwise.research.domain.model.IngestOutcome;
import com.briefwise.research.domain.model.IngestedFiling;
import com.briefwise.research.domain.port.FilingIngest;
import com.briefwise.shared.domain.model.Ticker;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code research}. Planned: ask, searchFilings (M2). Methods are added
 * with the milestone that implements them (DESIGN 6.2); no logic here, delegate only.
 */
@Component
public class ResearchFacade {

    private final FilingIngest ingest;

    ResearchFacade(FilingIngest ingest) {
        this.ingest = ingest;
    }

    /**
     * Fetches one filing of a company, screens its sections (ADR 0013) and stores it. Safe to repeat: the same
     * content hash leaves the stored filing as it is, a different one replaces its sections.
     *
     * @param accessionNo dashed ({@code 0000320193-26-000026}) or the 18 digits
     * @throws IllegalArgumentException if the accession number is malformed or the instrument has no filings on
     *         EDGAR (a fund)
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown or the source
     *         does not list the filing for the company
     * @throws com.briefwise.research.domain.model.FilingSourceUnavailableException if the source fails after retries
     * @throws IllegalStateException if the source is misconfigured or a snapshot file was edited
     */
    public IngestedFilingView ingestFiling(Ticker ticker, String accessionNo) {
        return view(ingest.ingestFiling(ticker, new AccessionNo(accessionNo)));
    }

    /**
     * Ingests the filings the corpus policy picks for one company (ADR 0025): the latest 10-K, the 10-Qs since, the
     * relevant 8-Ks of the lookback. A failing filing is reported and the others are ingested.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown
     * @throws IllegalStateException if the source is misconfigured or a snapshot file was edited
     */
    public FilingIngestSummaryView ingestFilings(Ticker ticker) {
        return view(ingest.ingestFilings(ticker));
    }

    /**
     * {@link #ingestFilings} for every company held in a portfolio; funds are reported as skipped.
     *
     * @throws IllegalStateException if the source is misconfigured or a snapshot file was edited
     */
    public FilingIngestSummaryView ingestHeld() {
        return view(ingest.ingestHeld());
    }

    private static IngestedFilingView view(IngestedFiling f) {
        return new IngestedFilingView(f.ticker(), f.ref().accessionNo().value(), f.ref().type().form(),
                f.ref().filedAt(), IngestOutcomeView.valueOf(f.outcome().name()), f.sections(), f.suspectedSections());
    }

    private static FilingIngestSummaryView view(FilingIngestSummary s) {
        return new FilingIngestSummaryView(s.companies(), s.skipped(), s.selected(),
                (int) s.count(IngestOutcome.INSERTED), (int) s.count(IngestOutcome.REPLACED),
                (int) s.count(IngestOutcome.UNCHANGED), s.sections(), s.suspectedSections(),
                s.ingested().stream().map(ResearchFacade::view).toList(),
                s.failed().stream().map(ResearchFacade::view).toList());
    }

    private static FailedFilingView view(FailedFiling f) {
        return new FailedFilingView(f.ticker(), f.accessionNo().map(AccessionNo::value), f.reason());
    }

    public enum IngestOutcomeView { INSERTED, REPLACED, UNCHANGED }

    /** {@code suspectedSections}: flagged by the injection screen, stored but never retrieved. */
    public record IngestedFilingView(Ticker ticker, String accessionNo, String form, LocalDate filedAt,
                                     IngestOutcomeView outcome, int sections, int suspectedSections) {}

    /** {@code accessionNo} is empty when the company's listing itself failed. */
    public record FailedFilingView(Ticker ticker, Optional<String> accessionNo, String reason) {}

    /**
     * {@code companies}: asked for; {@code skipped}: without EDGAR filings; {@code selected}: picked by the corpus
     * policy; then the outcome counts and every ingested and failed filing.
     */
    public record FilingIngestSummaryView(List<Ticker> companies, List<Ticker> skipped, int selected, int inserted,
                                          int replaced, int unchanged, int sections, int suspectedSections,
                                          List<IngestedFilingView> filings, List<FailedFilingView> failed) {}
}
