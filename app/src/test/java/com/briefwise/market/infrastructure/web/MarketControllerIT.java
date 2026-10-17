package com.briefwise.market.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.briefwise.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors the cases in docs/api.http: happy path, 404, 400. Inserted prices are rolled back after each test. */
@Transactional
class MarketControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private void insertClose(String date, String close) {
        jdbc.update("""
                INSERT INTO daily_prices (instrument_id, trade_date, close, volume)
                SELECT id, ?::date, ?::numeric, 1000 FROM instruments WHERE ticker = 'AAPL'
                """, date, close);
    }

    @Test
    void returns_price_history() throws Exception {
        insertClose("2026-10-14", "100");
        insertClose("2026-10-15", "102.5");

        mvc.perform(get("/api/market/instruments/aapl/prices").param("from", "2026-10-14").param("to", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-10-14"))
                .andExpect(jsonPath("$[1].close").value(102.5));
    }

    @Test
    void returns_the_daily_move() throws Exception {
        insertClose("2026-10-14", "100");
        insertClose("2026-10-15", "102.5");

        mvc.perform(get("/api/market/instruments/AAPL/daily-move"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.changePercent").value(2.5));
    }

    @Test
    void unknown_ticker_is_404() throws Exception {
        mvc.perform(get("/api/market/instruments/ZZZZ/daily-move"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void instrument_without_prices_is_404() throws Exception {
        mvc.perform(get("/api/market/instruments/MSFT/daily-move"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalid_ticker_is_400() throws Exception {
        mvc.perform(get("/api/market/instruments/123/daily-move"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_or_inverted_dates_are_400() throws Exception {
        mvc.perform(get("/api/market/instruments/AAPL/prices").param("from", "2026-10-14"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/market/instruments/AAPL/prices").param("from", "2026-10-15").param("to", "2026-10-14"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/market/instruments/AAPL/prices").param("from", "yesterday").param("to", "today"))
                .andExpect(status().isBadRequest());
    }
}
