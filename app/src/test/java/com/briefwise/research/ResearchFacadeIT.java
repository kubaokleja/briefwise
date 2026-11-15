package com.briefwise.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.research.ResearchFacade.FilingIngestSummaryView;
import com.briefwise.research.ResearchFacade.IngestOutcomeView;
import com.briefwise.research.ResearchFacade.IngestedFilingView;
import com.briefwise.research.domain.model.FilingNotFoundException;
import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ingest from the test snapshot ({@code src/test/resources/research/snapshot}: a synthetic AAPL 10-K and an 8-K whose
 * EX-99.1 contains an injection phrase) into Postgres. The 8-K is ingested by accession, because whether the
 * selection policy still picks it depends on today's date.
 */
class ResearchFacadeIT extends AbstractIntegrationTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final String TEN_K = "0000320193-26-000026";
    private static final String EIGHT_K = "0000320193-26-000024";

    @Autowired
    ResearchFacade facade;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void filing_with_an_injection_phrase_is_stored_flagged_and_repeating_changes_nothing() {
        IngestedFilingView first = facade.ingestFiling(AAPL, EIGHT_K);

        assertThat(first.outcome()).isIn(IngestOutcomeView.INSERTED, IngestOutcomeView.UNCHANGED);
        assertThat(first.form()).isEqualTo("8-K");
        assertThat(first.sections()).isEqualTo(2);
        assertThat(first.suspectedSections()).isEqualTo(1);
        List<String> rules = jdbc.queryForList("""
                SELECT array_to_string(s.matched_rules, ',')
                FROM research_filing_sections s JOIN research_filings f ON f.id = s.filing_id
                WHERE f.accession_no = ? AND s.injection_suspected""", String.class, EIGHT_K);
        assertThat(rules).containsExactly("instruction-override");

        IngestedFilingView again = facade.ingestFiling(AAPL, "000032019326000024");   // the 18-digit form
        assertThat(again.outcome()).isEqualTo(IngestOutcomeView.UNCHANGED);
        assertThat(again.suspectedSections()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM research_filings WHERE accession_no = ?", Long.class,
                EIGHT_K)).isEqualTo(1);
    }

    @Test
    void held_companies_are_ingested_once_funds_skipped() {
        FilingIngestSummaryView first = facade.ingestHeld();

        assertThat(first.companies()).contains(AAPL, new Ticker("MSFT"));
        assertThat(first.skipped()).contains(new Ticker("SPY"));
        assertThat(first.failed()).isEmpty();   // companies without a snapshot file list nothing
        assertThat(first.filings()).extracting(IngestedFilingView::accessionNo).contains(TEN_K);

        FilingIngestSummaryView second = facade.ingestHeld();
        assertThat(second.inserted()).isZero();
        assertThat(second.replaced()).isZero();
        assertThat(second.unchanged()).isEqualTo(second.selected());
    }

    @Test
    void unknown_filing_ticker_and_fund_are_rejected() {
        assertThatThrownBy(() -> facade.ingestFiling(AAPL, "0000320193-26-999999"))
                .isInstanceOf(FilingNotFoundException.class);
        assertThatThrownBy(() -> facade.ingestFiling(new Ticker("ZZZZ"), TEN_K))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> facade.ingestFiling(new Ticker("SPY"), TEN_K))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.ingestFiling(AAPL, "not-an-accession"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
