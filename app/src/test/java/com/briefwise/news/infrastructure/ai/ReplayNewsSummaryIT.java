package com.briefwise.news.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.aiops.AiopsFacade;
import com.briefwise.aiops.AiopsFacade.GenerationEntry;
import com.briefwise.aiops.AiopsFacade.ReplayRequest;
import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryDraft;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.shared.domain.model.TokenCount;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The {@code test} profile as CI runs it ({@code replay} + {@code on-miss: fail}, ADR 0015): a recorded summary is
 * replayed with zero HTTP requests and no new log row or usage; a missing recording defers, so the previous summary
 * stays.
 */
class ReplayNewsSummaryIT extends AbstractIntegrationTest {

    private static final String RECORDED = "{\"points\": [{\"text\": \"AAPL: Apple reported revenue below "
            + "estimates.\", \"newsIds\": [\"" + LlmNewsSummarizerIT.ITEM_ID + "\"]}]}";

    @Autowired
    LlmNewsSummarizer summarizer;

    @Autowired
    NewsSummaryPrompt prompt;

    @Autowired
    AiopsFacade aiops;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        jdbc.update("DELETE FROM ai_generation_log");
        jdbc.update("DELETE FROM ai_daily_usage");
    }

    @Test
    void recorded_summary_is_replayed_without_an_http_call_a_new_log_row_or_usage() {
        SummaryRequest request = LlmNewsSummarizerIT.request();
        String data = prompt.render(request);
        aiops.recordGeneration(new GenerationEntry("news-summary", "claude-haiku-5-5", Optional.empty(),
                prompt.version(), "recorded",
                Optional.of(RECORDED), new TokenCount(800, 120), Duration.ofMillis(900), false, Optional.empty(),
                Optional.of(new ReplayRequest("news-summary", "claude-haiku-5-5", prompt.version(), data))));

        SummarizerResult result = summarizer.summarize(request);

        assertThat(result).isEqualTo(new SummarizerResult.Drafted(new SummaryDraft(List.of(new SummaryDraft.Point(
                "AAPL: Apple reported revenue below estimates.", List.of(LlmNewsSummarizerIT.ITEM_ID))),
                "claude-haiku-5-5", prompt.version())));
        WIREMOCK.verify(0, postRequestedFor(urlPathEqualTo("/v1/messages")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_generation_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_daily_usage", Integer.class)).isZero();
    }

    @Test
    void missing_recording_defers_and_never_calls_the_model() {
        assertThat(summarizer.summarize(LlmNewsSummarizerIT.request()))
                .isEqualTo(new SummarizerResult.Deferred("no replay entry"));
        WIREMOCK.verify(0, postRequestedFor(urlPathEqualTo("/v1/messages")));
    }
}
