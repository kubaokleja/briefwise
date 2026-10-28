package com.briefwise.jobs.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.briefwise.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Mirrors the jobs cases in docs/api.http: happy path, 404, 400. */
class AdminJobControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM news_outbox");
        jdbc.update("DELETE FROM news_items");
    }

    @Test
    void runs_the_sweep_on_demand() throws Exception {
        mvc.perform(post("/api/admin/jobs/news-classify/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("news-classify"))
                .andExpect(jsonPath("$.trigger").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[0].name").value("sweep"))
                .andExpect(jsonPath("$.steps[0].result.picked").value(0));
    }

    @Test
    void unknown_job_is_404() throws Exception {
        mvc.perform(post("/api/admin/jobs/risk-alerts/run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown job: risk-alerts"));
    }

    @Test
    void malformed_job_id_is_400() throws Exception {
        mvc.perform(post("/api/admin/jobs/News_Ingest/run"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
