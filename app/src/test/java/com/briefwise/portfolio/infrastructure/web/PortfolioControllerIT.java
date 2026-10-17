package com.briefwise.portfolio.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.briefwise.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Mirrors the cases in docs/api.http: happy path, 404, 400. */
class PortfolioControllerIT extends AbstractIntegrationTest {

    private static final String CORE_BALANCED = "b0000000-0000-4000-8000-000000000002";

    @Autowired
    MockMvc mvc;

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
}
