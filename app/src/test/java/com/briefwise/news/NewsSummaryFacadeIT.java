package com.briefwise.news;

import static com.briefwise.news.SummaryFixtures.insertClassified;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.compliance.ComplianceFacade;
import com.briefwise.news.NewsFacade.PortfolioSummaryView;
import com.briefwise.news.NewsFacade.RelevantNewsView;
import com.briefwise.news.NewsFacade.SummaryOutcomeView;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryDraft;
import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import com.briefwise.shared.domain.model.SourceType;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Selection and the daily summary end to end over the seeded "Core Balanced" portfolio (AAPL 0.15, MSFT 0.15,
 * JPM 0.10, JNJ 0.10, SPY, AGG) with the real compliance check and repository; the LLM is the recording fake.
 */
class NewsSummaryFacadeIT extends AbstractIntegrationTest {

    static final UUID BALANCED = UUID.fromString("b0000000-0000-4000-8000-000000000002");

    @Autowired
    NewsFacade facade;

    @Autowired
    ComplianceFacade compliance;

    @Autowired
    RecordingNewsSummarizer summarizer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM news_portfolio_summaries");
        jdbc.update("DELETE FROM news_outbox");
        jdbc.update("DELETE FROM news_items");
        summarizer.reset();
    }

    private static SummarizerResult draft(String text, UUID newsId) {
        return new SummarizerResult.Drafted(new SummaryDraft(List.of(new SummaryDraft.Point(text, List.of(newsId))),
                "claude-haiku-5-5", "v1"));
    }

    @Test
    void relevant_news_is_filtered_to_held_tickers_and_ordered_by_relevance_then_weight() {
        UUID jpm = insertClassified(jdbc, "JPMorgan beats estimates", Relevance.HIGH, "JPM");
        UUID apple = insertClassified(jdbc, "Apple misses estimates", Relevance.HIGH, "AAPL");
        UUID msft = insertClassified(jdbc, "Microsoft minor update", Relevance.MEDIUM, "MSFT");
        insertClassified(jdbc, "Nvidia news", Relevance.HIGH, "NVDA");
        insertClassified(jdbc, "Apple listicle", Relevance.LOW, "AAPL");

        List<RelevantNewsView> news = facade.getRelevantNews(BALANCED);

        assertThat(news).extracting(RelevantNewsView::id).containsExactly(apple, jpm, msft);
        assertThat(news.getFirst().heldTickers()).containsExactly(new Ticker("AAPL"));
        assertThat(news.getFirst().weight()).isEqualByComparingTo("0.15");
    }

    @Test
    void compliant_summary_is_stored_with_citations_and_the_gate_keeps_it() {
        UUID apple = insertClassified(jdbc, "Apple misses estimates", Relevance.HIGH, "AAPL");
        summarizer.answer(request -> draft("AAPL: Apple reported revenue below estimates.", apple));

        assertThat(facade.summarizeForPortfolio(BALANCED)).isEqualTo(SummaryOutcomeView.GENERATED);
        assertThat(facade.summarizeForPortfolio(BALANCED)).isEqualTo(SummaryOutcomeView.UNCHANGED);

        assertThat(summarizer.calls()).hasSize(1);
        PortfolioSummaryView summary = facade.getLatestSummary(BALANCED).orElseThrow();
        assertThat(summary.aiGenerated()).isTrue();
        assertThat(summary.fallback()).isFalse();
        assertThat(summary.disclaimer()).isEqualTo(compliance.disclaimer());
        assertThat(summary.model()).contains("claude-haiku-5-5");
        assertThat(summary.points()).singleElement().satisfies(p -> assertThat(p.citations()).singleElement()
                .satisfies(c -> {
                    assertThat(c.type()).isEqualTo(SourceType.NEWS);
                    assertThat(c.sourceId()).isEqualTo(apple.toString());
                    assertThat(c.excerpt()).isEqualTo("Apple misses estimates");
                }));
    }

    @Test
    void forecast_language_twice_ends_in_the_template_flagged_as_fallback() {
        UUID apple = insertClassified(jdbc, "Apple misses estimates", Relevance.HIGH, "AAPL");
        summarizer.answer(request -> draft("AAPL shares will rise after the miss.", apple));

        assertThat(facade.summarizeForPortfolio(BALANCED)).isEqualTo(SummaryOutcomeView.FALLBACK);

        assertThat(summarizer.calls()).hasSize(2);
        assertThat(summarizer.calls().getLast().corrections()).singleElement()
                .satisfies(v -> assertThat(v.offendingText()).isEqualTo("will rise"));
        PortfolioSummaryView summary = facade.getLatestSummary(BALANCED).orElseThrow();
        assertThat(summary.fallback()).isTrue();
        assertThat(summary.aiGenerated()).isFalse();
        assertThat(summary.points()).extracting(p -> p.text())
                .containsExactly("AAPL: Apple misses estimates (negative, earnings)");
    }

    @Test
    void no_relevant_news_gives_an_empty_summary_without_a_call() {
        assertThat(facade.summarizeForPortfolio(BALANCED)).isEqualTo(SummaryOutcomeView.EMPTY);

        assertThat(summarizer.calls()).isEmpty();
        assertThat(facade.getLatestSummary(BALANCED)).hasValueSatisfying(s -> {
            assertThat(s.empty()).isTrue();
            assertThat(s.aiGenerated()).isFalse();
        });
    }

    @Test
    void unknown_portfolio_is_not_found() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> facade.getRelevantNews(unknown)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> facade.getLatestSummary(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }
}
