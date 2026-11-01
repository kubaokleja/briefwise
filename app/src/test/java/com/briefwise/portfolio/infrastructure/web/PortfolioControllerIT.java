package com.briefwise.portfolio.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.briefwise.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors the cases in docs/api.http: happy path, 404, 400. */
class PortfolioControllerIT extends AbstractIntegrationTest {

    private static final String CORE_BALANCED = "b0000000-0000-4000-8000-000000000002";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private void insertClose(String ticker, String date, String close) {
        jdbc.update("""
                INSERT INTO daily_prices (instrument_id, trade_date, close, volume)
                SELECT id, ?::date, ?::numeric, 1000 FROM instruments WHERE ticker = ?
                """, date, close, ticker);
    }

    @Test
    void lists_portfolios() throws Exception {
        mvc.perform(get("/api/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void gets_a_portfolio_and_returns_a_correlation_id() throws Exception {
        mvc.perform(get("/api/portfolios/" + CORE_BALANCED).header("X-Correlation-ID", "it-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "it-123"))
                .andExpect(jsonPath("$.name").value("Core Balanced"))
                .andExpect(jsonPath("$.riskProfile").value("BALANCED"))
                .andExpect(jsonPath("$.positions[0].ticker").value("SPY"));
    }

    @Test
    void gets_holdings() throws Exception {
        mvc.perform(get("/api/portfolios/" + CORE_BALANCED + "/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void unknown_portfolio_is_404() throws Exception {
        mvc.perform(get("/api/portfolios/00000000-0000-4000-8000-0000000000ff"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void malformed_id_is_400() throws Exception {
        mvc.perform(get("/api/portfolios/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void daily_move_weights_each_priced_position_and_lists_the_unpriced_ones() throws Exception {
        insertClose("AAPL", "2026-10-26", "100");
        insertClose("AAPL", "2026-10-27", "102.5");
        insertClose("MSFT", "2026-10-26", "200");
        insertClose("MSFT", "2026-10-27", "198");

        // 0.15 x 2.5 + 0.15 x -1 = 0.225
        mvc.perform(get("/api/portfolios/" + CORE_BALANCED + "/daily-move"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changePercent").value(0.225))
                .andExpect(jsonPath("$.pricedWeight").value(0.3))
                .andExpect(jsonPath("$.asOf").value("2026-10-27"))
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.positions[0].contribution").value(0.375))
                .andExpect(jsonPath("$.unpriced.length()").value(4));
    }

    @Test
    void daily_move_without_prices_has_no_change() throws Exception {
        mvc.perform(get("/api/portfolios/" + CORE_BALANCED + "/daily-move"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changePercent").doesNotExist())
                .andExpect(jsonPath("$.unpriced.length()").value(6));
    }

    @Test
    void daily_move_of_an_unknown_portfolio_is_404() throws Exception {
        mvc.perform(get("/api/portfolios/00000000-0000-4000-8000-0000000000ff/daily-move"))
                .andExpect(status().isNotFound());
    }
}
