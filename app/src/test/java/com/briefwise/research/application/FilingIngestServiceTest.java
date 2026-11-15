package com.briefwise.research.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.Company;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.Filing;
import com.briefwise.research.domain.model.FilingIngestSummary;
import com.briefwise.research.domain.model.FilingNotFoundException;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.model.FilingSection;
import com.briefwise.research.domain.model.FilingSelectionPolicy;
import com.briefwise.research.domain.model.FilingSourceUnavailableException;
import com.briefwise.research.domain.model.FilingType;
import com.briefwise.research.domain.model.IngestOutcome;
import com.briefwise.research.domain.model.IngestedFiling;
import com.briefwise.research.domain.model.ScreenedSection;
import com.briefwise.research.domain.model.Screening;
import com.briefwise.research.domain.port.Companies;
import com.briefwise.research.domain.port.FilingRepository;
import com.briefwise.research.domain.port.FilingSnapshotWriter;
import com.briefwise.research.domain.port.FilingSource;
import com.briefwise.research.domain.port.UntrustedContentScreen;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FilingIngestServiceTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker SPY = new Ticker("SPY");
    private static final Cik APPLE = new Cik("320193");
    private static final Cik MICROSOFT = new Cik("789019");
    private static final Company APPLE_INC = new Company(AAPL, Company.Kind.COMPANY, Optional.of(APPLE));
    private static final Company MICROSOFT_CORP = new Company(MSFT, Company.Kind.COMPANY, Optional.of(MICROSOFT));
    private static final Company SPY_FUND = new Company(SPY, Company.Kind.FUND, Optional.empty());
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T08:00:00Z"), ZoneOffset.UTC);

    private static final FilingRef TEN_K = ref("0000320193-26-000026", APPLE, FilingType.TEN_K, "2026-11-03", Set.of());
    private static final FilingRef EIGHT_K = ref("0000320193-26-000024", APPLE, FilingType.EIGHT_K, "2026-11-02",
            Set.of("2.02", "9.01"));
    private static final FilingRef OLD_EIGHT_K = ref("0000320193-25-000071", APPLE, FilingType.EIGHT_K, "2025-08-01",
            Set.of("2.02"));
    private static final FilingRef MSFT_TEN_K = ref("0000950170-26-000083", MICROSOFT, FilingType.TEN_K,
            "2026-07-30", Set.of());

    private final Companies companies = mock();
    private final FilingSource source = mock();
    private final FilingSnapshotWriter snapshot = mock();
    private final FilingRepository repository = mock();
    private final UntrustedContentScreen screen = mock();
    private FilingIngestService service;

    @BeforeEach
    void setUp() {
        service = new FilingIngestService(companies, source, snapshot, repository, screen,
                new FilingSelectionPolicy(365), CLOCK);
        when(companies.get(AAPL)).thenReturn(APPLE_INC);
        when(companies.get(MSFT)).thenReturn(MICROSOFT_CORP);
        when(companies.get(SPY)).thenReturn(SPY_FUND);
        when(source.list(APPLE)).thenReturn(List.of(OLD_EIGHT_K, EIGHT_K, TEN_K));
        when(source.list(MICROSOFT)).thenReturn(List.of(MSFT_TEN_K));
        when(source.fetch(any())).thenAnswer(call -> fetched(call.getArgument(0), "Text of " + call.getArgument(0)));
        when(repository.findByAccession(any())).thenReturn(Optional.empty());
        when(screen.screen(any(), anyString())).thenReturn(Screening.CLEAN);
    }

    private static FilingRef ref(String accession, Cik cik, FilingType type, String filedAt, Set<String> items) {
        return new FilingRef(new AccessionNo(accession), cik, type, LocalDate.parse(filedAt), Optional.empty(),
                "doc.htm", items);
    }

    private static FetchedFiling fetched(FilingRef ref, String text) {
        return new FetchedFiling(ref, "https://www.sec.gov/x/" + ref.accessionNo().withoutDashes(), "html-1/sections-2",
                List.of(new FilingSection("1", "Business", text), new FilingSection("1A", "Risk Factors", "Risks.")));
    }

    private static Filing stored(FetchedFiling fetched, Screening screening) {
        return new Filing(UUID.randomUUID(), AAPL, fetched.ref(), fetched.sourceUrl(), fetched.extractorVersion(),
                fetched.contentSha256(), fetched.sections().stream()
                .map(s -> new ScreenedSection(UUID.randomUUID(), s, screening)).toList());
    }

    @Test
    void new_filing_is_written_to_the_snapshot_screened_and_inserted() {
        IngestedFiling result = service.ingestFiling(AAPL, TEN_K.accessionNo());

        assertThat(result.outcome()).isEqualTo(IngestOutcome.INSERTED);
        assertThat(result.sections()).isEqualTo(2);
        verify(snapshot).write(eq(AAPL), any());
        ArgumentCaptor<Filing> filing = ArgumentCaptor.forClass(Filing.class);
        verify(repository).insert(filing.capture());
        assertThat(filing.getValue().ref()).isEqualTo(TEN_K);
        assertThat(filing.getValue().ticker()).isEqualTo(AAPL);
        ScreenedSection first = filing.getValue().sections().getFirst();
        verify(screen).screen(first.id(), "Business\nText of " + TEN_K);   // title + text, content id = section id
    }

    @Test
    void same_content_hash_is_left_as_stored_and_not_screened_again() {
        when(repository.findByAccession(TEN_K.accessionNo()))
                .thenReturn(Optional.of(stored(fetched(TEN_K, "Text of " + TEN_K), new Screening(true, List.of("x")))));

        IngestedFiling result = service.ingestFiling(AAPL, TEN_K.accessionNo());

        assertThat(result.outcome()).isEqualTo(IngestOutcome.UNCHANGED);
        assertThat(result.suspectedSections()).isEqualTo(2);   // the stored screening
        verify(screen, never()).screen(any(), anyString());
        verify(repository, never()).insert(any());
        verify(repository, never()).replace(any());
    }

    @Test
    void changed_content_hash_replaces_the_stored_filing() {
        when(repository.findByAccession(TEN_K.accessionNo()))
                .thenReturn(Optional.of(stored(fetched(TEN_K, "Older extraction"), Screening.CLEAN)));

        IngestedFiling result = service.ingestFiling(AAPL, TEN_K.accessionNo());

        assertThat(result.outcome()).isEqualTo(IngestOutcome.REPLACED);
        verify(repository).replace(any());
        verify(repository, never()).insert(any());
    }

    @Test
    void suspected_section_is_stored_with_its_rules() {
        when(screen.screen(any(), eq("Risk Factors\nRisks."))).thenReturn(
                new Screening(true, List.of("instruction-override")));

        IngestedFiling result = service.ingestFiling(AAPL, TEN_K.accessionNo());

        assertThat(result.suspectedSections()).isEqualTo(1);
        ArgumentCaptor<Filing> filing = ArgumentCaptor.forClass(Filing.class);
        verify(repository).insert(filing.capture());
        assertThat(filing.getValue().sections().get(1).screening().matchedRules())
                .containsExactly("instruction-override");
    }

    @Test
    void accession_not_listed_for_the_company_is_not_found() {
        assertThatThrownBy(() -> service.ingestFiling(AAPL, MSFT_TEN_K.accessionNo()))
                .isInstanceOf(FilingNotFoundException.class)
                .hasMessageContaining("0000950170-26-000083");
        verify(source, never()).fetch(any());
    }

    @Test
    void a_fund_has_no_filings_to_ingest() {
        assertThatThrownBy(() -> service.ingestFiling(SPY, TEN_K.accessionNo()))
                .isInstanceOf(IllegalArgumentException.class);

        FilingIngestSummary summary = service.ingestFilings(SPY);
        assertThat(summary.skipped()).containsExactly(SPY);
        assertThat(summary.selected()).isZero();
        verify(source, never()).list(any());
    }

    @Test
    void ingest_filings_takes_what_the_selection_policy_picks() {
        FilingIngestSummary summary = service.ingestFilings(AAPL);

        assertThat(summary.selected()).isEqualTo(2);   // the 8-K of 2025-08 is outside the 365 days
        assertThat(summary.ingested()).extracting(IngestedFiling::ref).containsExactly(EIGHT_K, TEN_K);
        assertThat(summary.count(IngestOutcome.INSERTED)).isEqualTo(2);
        assertThat(summary.sections()).isEqualTo(4);
        verify(source, never()).fetch(OLD_EIGHT_K);
    }

    @Test
    void a_failing_filing_is_reported_and_the_next_one_is_ingested() {
        when(source.fetch(EIGHT_K)).thenThrow(new FilingNotFoundException("gone"));

        FilingIngestSummary summary = service.ingestFilings(AAPL);

        assertThat(summary.ingested()).extracting(IngestedFiling::ref).containsExactly(TEN_K);
        assertThat(summary.failed()).singleElement().satisfies(f -> {
            assertThat(f.accessionNo()).contains(EIGHT_K.accessionNo());
            assertThat(f.reason()).isEqualTo("FilingNotFoundException: gone");
        });
    }

    @Test
    void held_companies_go_on_after_a_failed_listing() {
        when(companies.held()).thenReturn(List.of(APPLE_INC, MICROSOFT_CORP, SPY_FUND));
        when(source.list(APPLE)).thenThrow(new FilingSourceUnavailableException("EDGAR down", null));

        FilingIngestSummary summary = service.ingestHeld();

        assertThat(summary.companies()).containsExactly(AAPL, MSFT, SPY);
        assertThat(summary.skipped()).containsExactly(SPY);
        assertThat(summary.failed()).singleElement().satisfies(f -> {
            assertThat(f.ticker()).isEqualTo(AAPL);
            assertThat(f.accessionNo()).isEmpty();
        });
        assertThat(summary.ingested()).extracting(IngestedFiling::ref).containsExactly(MSFT_TEN_K);
    }

    @Test
    void a_misconfigured_source_stops_the_run() {
        when(companies.held()).thenReturn(List.of(APPLE_INC, MICROSOFT_CORP));
        when(source.fetch(EIGHT_K)).thenThrow(new IllegalStateException("EDGAR answered 403"));

        assertThatThrownBy(() -> service.ingestHeld()).isInstanceOf(IllegalStateException.class);
        verify(source, never()).list(MICROSOFT);
    }
}
