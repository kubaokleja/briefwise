package com.briefwise.news.infrastructure.web;

import static com.briefwise.news.SummaryFixtures.insertClassified;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.NewsFacade;
import com.briefwise.news.RecordingNewsSummarizer;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryDraft;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Mirrors the news cases in docs/api.http: happy path, 404 (unknown portfolio, no summary yet), 400. */
class PortfolioNewsControllerIT extends AbstractIntegrationTest {

    private static final String BALANCED = "b0000000-0000-4000-8000-000000000002";

    @Autowired
    MockMvc mvc;

    @Autowired
    NewsFacade facade;

    @Autowired
    RecordingNewsSummarizer summarizer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM news_portfolio_summaries");
        jdbc.update("DELETE FROM news_outbox");
        jdbc.update("DELETE FROM news_items");
        summarizer.reset();
    }

    @Test
    void relevant_news_of_a_portfolio() throws Exception {
        UUID apple = insertClassified(jdbc, "Apple misses estimates", Relevance.HIGH, "AAPL");

        mvc.perform(get("/api/portfolios/{id}/news/relevant", BALANCED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(apple.toString()))
                .andExpect(jsonPath("$[0].tickers[0]").value("AAPL"))
                .andExpect(jsonPath("$[0].relevance").value("HIGH"))
                .andExpect(jsonPath("$[0].sentiment").value("NEGATIVE"))
                .andExpect(jsonPath("$[0].weight").value(0.15));
    }

    @Test
    void latest_summary_with_ai_marker_fields_citations_and_disclaimer() throws Exception {
        UUID apple = insertClassified(jdbc, "Apple misses estimates", Relevance.HIGH, "AAPL");
        summarizer.answer(request -> new SummarizerResult.Drafted(new SummaryDraft(List.of(
                new SummaryDraft.Point("AAPL: Apple reported revenue below estimates.", List.of(apple))),
                "claude-haiku-5-5", "v1")));
        facade.summarizeForPortfolio(UUID.fromString(BALANCED));

        mvc.perform(get("/api/portfolios/{id}/news/summary", BALANCED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiGenerated").value(true))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
                .andExpect(jsonPath("$.points[0].text").value("AAPL: Apple reported revenue below estimates."))
                .andExpect(jsonPath("$.points[0].citations[0].type").value("NEWS"))
                .andExpect(jsonPath("$.points[0].citations[0].sourceId").value(apple.toString()))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty());
    }

    @Test
    void no_summary_yet_is_404() throws Exception {
        mvc.perform(get("/api/portfolios/{id}/news/summary", BALANCED))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No news summary for portfolio " + BALANCED + " yet"));
    }

    @Test
    void unknown_portfolio_is_404() throws Exception {
        mvc.perform(get("/api/portfolios/{id}/news/relevant", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformed_portfolio_id_is_400() throws Exception {
        mvc.perform(get("/api/portfolios/{id}/news/summary", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
