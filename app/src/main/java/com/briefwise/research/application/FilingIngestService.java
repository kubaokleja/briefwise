package com.briefwise.research.application;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.Company;
import com.briefwise.research.domain.model.FailedFiling;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.Filing;
import com.briefwise.research.domain.model.FilingIngestSummary;
import com.briefwise.research.domain.model.FilingNotFoundException;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.model.FilingSection;
import com.briefwise.research.domain.model.FilingSelectionPolicy;
import com.briefwise.research.domain.model.IngestOutcome;
import com.briefwise.research.domain.model.IngestedFiling;
import com.briefwise.research.domain.model.ScreenedSection;
import com.briefwise.research.domain.port.Companies;
import com.briefwise.research.domain.port.FilingIngest;
import com.briefwise.research.domain.port.FilingRepository;
import com.briefwise.research.domain.port.FilingSnapshotWriter;
import com.briefwise.research.domain.port.FilingSource;
import com.briefwise.research.domain.port.UntrustedContentScreen;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Filing ingest (ADR 0025, 0026). Per filing: fetch, keep in the snapshot (a no-op when the snapshot is the source),
 * compare the content hash with the stored one, and only for new or changed content screen every section (ADR 0013)
 * and store it. Each filing is stored in its own transaction, so a failure keeps the filings before it.
 *
 * <p>A filing whose hash is unchanged keeps its sections and stored screening (a change to the pattern list re-screens
 * nothing); only a new extractor version or source URL is recorded (decided 2026-11-15).
 *
 * <p>{@link IllegalStateException} (misconfigured source, edited snapshot file) stops the run: every following
 * filing would fail the same way. Every other exception of one filing or listing is reported and the run goes on.
 */
@Service
class FilingIngestService implements FilingIngest {

    private static final Logger log = LoggerFactory.getLogger(FilingIngestService.class);

    private final Companies companies;
    private final FilingSource source;
    private final FilingSnapshotWriter snapshot;
    private final FilingRepository repository;
    private final UntrustedContentScreen screen;
    private final FilingSelectionPolicy policy;
    private final Clock clock;

    FilingIngestService(Companies companies, FilingSource source, FilingSnapshotWriter snapshot,
                        FilingRepository repository, UntrustedContentScreen screen, FilingSelectionPolicy policy,
                        Clock clock) {
        this.companies = companies;
        this.source = source;
        this.snapshot = snapshot;
        this.repository = repository;
        this.screen = screen;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public IngestedFiling ingestFiling(Ticker ticker, AccessionNo accessionNo) {
        Company company = companies.get(ticker);
        if (!company.filesOnEdgar()) {
            throw new IllegalArgumentException(ticker.symbol() + " has no filings on EDGAR (fund or no CIK)");
        }
        Cik cik = company.cik().orElseThrow();
        FilingRef ref = source.list(cik).stream()
                .filter(r -> r.accessionNo().equals(accessionNo))
                .findFirst()
                .orElseThrow(() -> new FilingNotFoundException(
                        "Filing " + accessionNo + " is not listed for " + ticker.symbol() + " (CIK " + cik + ")"));
        return ingest(ticker, ref);
    }

    @Override
    public FilingIngestSummary ingestFilings(Ticker ticker) {
        Run run = new Run();
        run.company(companies.get(ticker));
        return run.summary();
    }

    @Override
    public FilingIngestSummary ingestHeld() {
        Run run = new Run();
        companies.held().forEach(run::company);
        return run.summary();
    }

    private IngestedFiling ingest(Ticker ticker, FilingRef ref) {
        FetchedFiling fetched = source.fetch(ref);
        snapshot.write(ticker, fetched);
        String hash = fetched.contentSha256();
        Optional<Filing> stored = repository.findByAccession(ref.accessionNo());
        if (stored.isPresent() && stored.get().contentSha256().equals(hash)) {
            if (!stored.get().extractorVersion().equals(fetched.extractorVersion())
                    || !stored.get().sourceUrl().equals(fetched.sourceUrl())) {
                // same text from a newer extractor: keep sections and screening, record the new provenance
                repository.updateProvenance(ref.accessionNo(), fetched.sourceUrl(), fetched.extractorVersion());
            }
            log.debug("Filing {} {} unchanged", ticker.symbol(), ref.accessionNo());
            return new IngestedFiling(ticker, ref, IngestOutcome.UNCHANGED, stored.get().sections().size(),
                    (int) stored.get().suspectedSections());
        }
        Filing filing = new Filing(UUID.randomUUID(), ticker, ref, fetched.sourceUrl(), fetched.extractorVersion(),
                hash, fetched.sections().stream().map(this::screened).toList());
        IngestOutcome outcome;
        if (stored.isPresent()) {
            repository.replace(filing);
            outcome = IngestOutcome.REPLACED;
        } else {
            repository.insert(filing);
            outcome = IngestOutcome.INSERTED;
        }
        log.info("Filing {} {} {} filed {}: {}, {} sections, {} suspected", ticker.symbol(), ref.type().form(),
                ref.accessionNo(), ref.filedAt(), outcome, filing.sections().size(), filing.suspectedSections());
        return new IngestedFiling(ticker, ref, outcome, filing.sections().size(), (int) filing.suspectedSections());
    }

    /** The section id is also the content id of the screening, so an {@code InjectionSuspected} event points at it. */
    private ScreenedSection screened(FilingSection section) {
        UUID id = UUID.randomUUID();
        return new ScreenedSection(id, section, screen.screen(id, section.title() + "\n" + section.text()));
    }

    private static String reason(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** Collects one run; per-company and per-filing failures are recorded here instead of thrown. */
    private final class Run {

        private final List<Ticker> asked = new ArrayList<>();
        private final List<Ticker> skipped = new ArrayList<>();
        private final List<IngestedFiling> ingested = new ArrayList<>();
        private final List<FailedFiling> failed = new ArrayList<>();
        private int selected;

        void company(Company company) {
            Ticker ticker = company.ticker();
            asked.add(ticker);
            if (!company.filesOnEdgar()) {
                skipped.add(ticker);
                return;
            }
            List<FilingRef> picked;
            try {
                picked = policy.select(source.list(company.cik().orElseThrow()), LocalDate.now(clock));
            } catch (IllegalStateException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("Filing listing of {} failed", ticker.symbol(), e);
                failed.add(new FailedFiling(ticker, Optional.empty(), reason(e)));
                return;
            }
            selected += picked.size();
            for (FilingRef ref : picked) {
                try {
                    ingested.add(ingest(ticker, ref));
                } catch (IllegalStateException e) {
                    throw e;
                } catch (RuntimeException e) {
                    log.warn("Filing {} {} failed", ticker.symbol(), ref.accessionNo(), e);
                    failed.add(new FailedFiling(ticker, Optional.of(ref.accessionNo()), reason(e)));
                }
            }
        }

        FilingIngestSummary summary() {
            FilingIngestSummary summary = new FilingIngestSummary(asked, skipped, selected, ingested, failed);
            log.info("Filing ingest: {} companies ({} skipped), {} selected, {} inserted, {} replaced, {} unchanged, "
                            + "{} failed, {} sections, {} suspected", asked.size(), skipped.size(), selected,
                    summary.count(IngestOutcome.INSERTED), summary.count(IngestOutcome.REPLACED),
                    summary.count(IngestOutcome.UNCHANGED), failed.size(), summary.sections(),
                    summary.suspectedSections());
            return summary;
        }
    }
}
