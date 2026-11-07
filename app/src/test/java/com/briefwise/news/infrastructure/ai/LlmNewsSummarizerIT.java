package com.briefwise.news.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryDraft;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The real summary adapter against WireMock standing in for the Anthropic Messages API (never a live call): request
 * shape, valid, malformed then re-ask, malformed twice, a cited id outside the request, timeout, 529, budget, and
 * record-then-replay. Replay on with {@code on-miss: fallback} and the log emptied before each test, as in
 * {@code LlmNewsClassifierIT}. Replay with {@code fail}: {@code ReplayNewsSummaryIT}.
 */
@TestPropertySource(properties = "briefwise.llm.replay.on-miss=fallback")
class LlmNewsSummarizerIT extends AbstractIntegrationTest {

    private static final String MESSAGES = "/v1/messages";
    static final UUID ITEM_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID PORTFOLIO = UUID.fromString("b0000000-0000-4000-8000-000000000002");

    @Autowired
    LlmNewsSummarizer summarizer;

    @Autowired
    NewsSummaryPrompt prompt;

    @Autowired
    JdbcTemplate jdbc;

    static SummaryRequest request() {
        ClassifiedNews news = new ClassifiedNews(ITEM_ID, "Apple misses quarterly revenue estimates",
                "iPhone sales fell short of forecasts.", "https://example.com/apple-miss", "Reuters",
                Instant.parse("2026-10-27T21:00:00Z"), Instant.parse("2026-10-28T04:31:00Z"),
                Set.of(new Ticker("AAPL")), Sentiment.NEGATIVE, Relevance.HIGH, EventType.EARNINGS,
                "Revenue missed estimates.");
        return new SummaryRequest(PORTFOLIO, List.of(new RelevantNews(news, Set.of(new Ticker("AAPL")),
                new BigDecimal("0.15"))), List.of());
    }

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        jdbc.update("DELETE FROM ai_generation_log");
        jdbc.update("DELETE FROM ai_daily_usage");
    }

    private static String fixture(String name) {
        try (InputStream in = LlmNewsSummarizerIT.class.getResourceAsStream("/anthropic/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Map<String, Object>> logRows() {
        return jdbc.queryForList("SELECT * FROM ai_generation_log ORDER BY created_at, id");
    }

    @Test
    void valid_answer_is_a_draft_logged_against_the_portfolio_and_counted() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("summary-valid.json"))));

        SummarizerResult result = summarizer.summarize(request());

        assertThat(result).isInstanceOf(SummarizerResult.Drafted.class);
        SummarizerResult.Drafted drafted = (SummarizerResult.Drafted) result;
        assertThat(drafted.draft()).isEqualTo(new SummaryDraft(List.of(new SummaryDraft.Point(
                "AAPL: Apple reported quarterly revenue below analyst estimates.", List.of(ITEM_ID))),
                "claude-haiku-5-5", "v1"));
        assertThat(drafted.generationId()).contains((UUID) logRows().getFirst().get("id"));
        assertThat(logRows()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("feature", "news-summary")
                .containsEntry("model", "claude-haiku-5-5")
                .containsEntry("prompt_version", "v1")
                .containsEntry("prompt_tokens", 820)
                .containsEntry("completion_tokens", 140)
                .containsEntry("is_fallback", false)
                .containsEntry("subject_id", PORTFOLIO));
        // 820 x 0.10 + 140 x 0.50 = 82 + 70 = 152 per 1M tokens
        assertThat((BigDecimal) logRows().getFirst().get("cost_usd")).isEqualByComparingTo("0.000152");
        assertThat(jdbc.queryForObject("SELECT call_count FROM ai_daily_usage WHERE feature = ? AND usage_date = ?",
                Integer.class, "news-summary", LocalDate.now(ZoneOffset.UTC))).isEqualTo(1);
    }

    @Test
    void request_uses_native_structured_output_low_effort_and_explicit_max_tokens() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("summary-valid.json"))));

        summarizer.summarize(request());

        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES))
                .withRequestBody(matchingJsonPath("$.model", equalTo("claude-haiku-5-5")))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("2048")))
                .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("low")))
                .withRequestBody(matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
                .withRequestBody(matchingJsonPath("$.output_config.format.schema.properties.points"))
                .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo(prompt.render(request()))))
                .withRequestBody(notContaining("\"temperature\""))
                .withRequestBody(notContaining("Core Balanced")));
    }

    @Test
    void malformed_answer_is_asked_again_and_both_calls_are_logged() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).inScenario("re-ask").whenScenarioStateIs(STARTED)
                .willReturn(okJson(fixture("summary-malformed.json"))).willSetStateTo("second"));
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).inScenario("re-ask").whenScenarioStateIs("second")
                .willReturn(okJson(fixture("summary-valid.json"))));

        assertThat(summarizer.summarize(request())).isInstanceOf(SummarizerResult.Drafted.class);

        assertThat(logRows()).hasSize(2);
        assertThat(logRows()).extracting(row -> row.get("replay_key") != null).containsExactlyInAnyOrder(false, true);
    }

    @Test
    void malformed_answer_twice_fails_as_invalid_output_and_flags_the_last_call() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("summary-malformed.json"))));

        assertThat(summarizer.summarize(request()))
                .isEqualTo(new SummarizerResult.Failed(LlmNewsSummarizer.INVALID_OUTPUT));
        WIREMOCK.verify(2, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).extracting(row -> row.get("is_fallback")).containsExactlyInAnyOrder(false, true);
    }

    @Test
    void citing_an_item_that_was_not_in_the_request_is_invalid_output() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("summary-unknown-id.json"))));

        assertThat(summarizer.summarize(request()))
                .isEqualTo(new SummarizerResult.Failed(LlmNewsSummarizer.INVALID_OUTPUT));
    }

    @Test
    void timeout_is_a_provider_error_without_a_re_ask() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES))
                .willReturn(okJson(fixture("summary-valid.json")).withFixedDelay(4_000)));

        assertThat(summarizer.summarize(request()))
                .isEqualTo(new SummarizerResult.Failed(LlmNewsSummarizer.PROVIDER_ERROR));
        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).singleElement().satisfies(row -> assertThat(row).containsEntry("is_fallback", true));
    }

    @Test
    void overloaded_provider_is_a_provider_error() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(aResponse().withStatus(529)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}")));

        assertThat(summarizer.summarize(request()))
                .isEqualTo(new SummarizerResult.Failed(LlmNewsSummarizer.PROVIDER_ERROR));
    }

    @Test
    void used_up_budget_defers_without_any_call() {
        jdbc.update("""
                INSERT INTO ai_daily_usage (usage_date, feature, call_count, cost_usd) VALUES (?, ?, 1, 1.00)
                """, LocalDate.now(ZoneOffset.UTC), "news-summary");

        assertThat(summarizer.summarize(request())).isInstanceOf(SummarizerResult.Deferred.class);
        WIREMOCK.verify(0, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).isEmpty();
    }

    @Test
    void recorded_answer_is_replayed_for_the_same_input_without_a_call_or_usage() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("summary-valid.json"))));
        SummarizerResult live = summarizer.summarize(request());

        SummarizerResult replayed = summarizer.summarize(request());

        // Same draft; a replayed answer is not logged again, so it carries no generation log entry.
        assertThat(((SummarizerResult.Drafted) replayed).draft()).isEqualTo(((SummarizerResult.Drafted) live).draft());
        assertThat(((SummarizerResult.Drafted) replayed).generationId()).isEmpty();
        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).hasSize(1);
    }
}
