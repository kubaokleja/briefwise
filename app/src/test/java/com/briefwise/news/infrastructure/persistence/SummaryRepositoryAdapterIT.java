package com.briefwise.news.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.domain.model.PortfolioSummary;
import com.briefwise.news.domain.model.SummaryPoint;
import com.briefwise.news.domain.model.SummaryViolation;
import com.briefwise.news.domain.port.SummaryRepository;
import com.briefwise.shared.domain.model.Citation;
import com.briefwise.shared.domain.model.SourceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@code news_portfolio_summaries} (V15): JSONB round trip, one row per portfolio and date, latest first. */
class SummaryRepositoryAdapterIT extends AbstractIntegrationTest {

    private static final UUID PORTFOLIO = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.parse("2026-10-28");
    private static final Instant AT = Instant.parse("2026-10-28T04:40:00Z");

    @Autowired
    SummaryRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM news_portfolio_summaries");
    }

    private static PortfolioSummary generated(LocalDate date, Instant at, String text) {
        Citation citation = new Citation(SourceType.NEWS, UUID.randomUUID().toString(), "https://example.com/a",
                "Apple misses \"estimates\"");
        return new PortfolioSummary(UUID.randomUUID(), PORTFOLIO, date, at,
                List.of(new SummaryPoint(text, List.of(citation))), false, Optional.of("claude-haiku-5-5"),
                Optional.of("v1"), "Disclaimer text.", List.of());
    }

    @Test
    void summary_reads_back_with_points_citations_and_metadata() {
        PortfolioSummary summary = generated(DAY, AT, "AAPL: revenue below estimates.");

        repository.save(summary);

        assertThat(repository.findLatest(PORTFOLIO)).contains(summary);
    }

    @Test
    void same_day_replaces_the_row_and_the_latest_day_wins() {
        repository.save(generated(DAY.minusDays(1), AT.minusSeconds(86_400), "Yesterday."));
        repository.save(generated(DAY, AT, "First today."));
        PortfolioSummary fallback = new PortfolioSummary(UUID.randomUUID(), PORTFOLIO, DAY, AT.plusSeconds(600),
                List.of(new SummaryPoint("AAPL: headline (negative, earnings)", List.of())), true, Optional.empty(),
                Optional.empty(), "Disclaimer text.", List.of(new SummaryViolation("NO_RECOMMENDATION", "buy the stock")));

        repository.save(fallback);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM news_portfolio_summaries", Integer.class)).isEqualTo(2);
        PortfolioSummary latest = repository.findLatest(PORTFOLIO).orElseThrow();
        assertThat(latest.fallback()).isTrue();
        assertThat(latest.model()).isEmpty();
        assertThat(latest.generatedAt()).isEqualTo(AT.plusSeconds(600));
        assertThat(latest.violations()).containsExactly(new SummaryViolation("NO_RECOMMENDATION", "buy the stock"));
    }

    @Test
    void empty_summary_has_no_points_and_no_model() {
        repository.save(new PortfolioSummary(UUID.randomUUID(), PORTFOLIO, DAY, AT, List.of(), false,
                Optional.empty(), Optional.empty(), "Disclaimer text.", List.of()));

        assertThat(repository.findLatest(PORTFOLIO)).hasValueSatisfying(s -> assertThat(s.empty()).isTrue());
        assertThat(repository.findLatest(UUID.randomUUID())).isEmpty();
    }
}
