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
import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.model.UnclassifiedReason;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The real LLM adapter against WireMock standing in for the Anthropic Messages API (never a live call). Covers the
 * request shape (ADR 0012), valid, malformed, re-ask, timeout and provider error, the budget gate, and that every
 * attempt lands in {@code ai_generation_log} and the daily usage. Replay is on with {@code on-miss: fallback}
 * (ADR 0015) and the log is emptied before each test, so every first call is a miss that reaches WireMock; the last
 * test shows the recorded answer being replayed. Replay with {@code fail}: {@code ReplayNewsClassificationIT}.
 */
@TestPropertySource(properties = "briefwise.llm.replay.on-miss=fallback")
class LlmNewsClassifierIT extends AbstractIntegrationTest {

    private static final String MESSAGES = "/v1/messages";

    @Autowired
    LlmNewsClassifier classifier;

    @Autowired
    NewsClassificationPrompt prompt;

    @Autowired
    JdbcTemplate jdbc;

    private final NewsItem item = new NewsItem(UUID.randomUUID(), "Apple beats quarterly revenue estimates",
            "iPhone sales rose 8%.", "https://example.com/apple", Instant.parse("2026-10-24T06:00:00Z"), "Reuters",
            Set.of(new Ticker("AAPL")), NewsStatus.RECEIVED, Optional.empty());

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        jdbc.update("DELETE FROM ai_generation_log");
        jdbc.update("DELETE FROM ai_daily_usage");
    }

    private static String fixture(String name) {
        try (InputStream in = LlmNewsClassifierIT.class.getResourceAsStream("/anthropic/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Map<String, Object>> logRows() {
        return jdbc.queryForList("SELECT * FROM ai_generation_log ORDER BY created_at, id");
    }

    @Test
    void valid_answer_is_classified_logged_with_cache_tokens_and_counted() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("classification-valid.json"))));

        ClassifierResult result = classifier.classify(item);

        assertThat(result).isEqualTo(new ClassifierResult.Classified(new NewsClassification(Sentiment.POSITIVE,
                Relevance.HIGH, EventType.EARNINGS, Set.of(new Ticker("AAPL")),
                "Quarterly revenue beat expectations.", "v1")));
        assertThat(logRows()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("feature", "news-classification")
                .containsEntry("model", "claude-haiku-5-5")
                .containsEntry("prompt_version", "v1")
                .containsEntry("prompt_tokens", 92)
                .containsEntry("completion_tokens", 61)
                .containsEntry("cache_read_tokens", 1180)
                .containsEntry("is_fallback", false)
                .containsEntry("subject_id", item.id()));
        // 92 x 0.10 + 61 x 0.50 + 1,180 x 0.01 = 9.2 + 30.5 + 11.8 = 51.5 per 1M tokens
        assertThat((BigDecimal) logRows().getFirst().get("cost_usd")).isEqualByComparingTo("0.0000515");
        assertThat(jdbc.queryForObject("SELECT call_count FROM ai_daily_usage WHERE feature = ? AND usage_date = ?",
                Integer.class, "news-classification", LocalDate.now(ZoneOffset.UTC))).isEqualTo(1);
    }

    @Test
    void request_uses_native_structured_output_low_effort_explicit_max_tokens_and_a_cached_system_prompt() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("classification-valid.json"))));

        classifier.classify(item);

        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES))
                .withHeader("x-api-key", equalTo("test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("claude-haiku-5-5")))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("1024")))
                .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("low")))
                .withRequestBody(matchingJsonPath("$.output_config.format.schema.properties.eventType"))
                .withRequestBody(matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
                .withRequestBody(matchingJsonPath("$.system[0].cache_control.type", equalTo("ephemeral")))
                .withRequestBody(matchingJsonPath("$.system[0].cache_control.ttl", equalTo("5m")))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        equalTo(prompt.render(item))))
                .withRequestBody(notContaining("\"temperature\"")));
    }

    @Test
    void malformed_answer_is_asked_again_and_both_calls_are_logged() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).inScenario("re-ask").whenScenarioStateIs(STARTED)
                .willReturn(okJson(fixture("classification-malformed.json"))).willSetStateTo("second"));
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).inScenario("re-ask").whenScenarioStateIs("second")
                .willReturn(okJson(fixture("classification-valid.json"))));

        ClassifierResult result = classifier.classify(item);

        assertThat(result).isInstanceOf(ClassifierResult.Classified.class);
        List<Map<String, Object>> rows = logRows();
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row).containsEntry("is_fallback", false));
        assertThat(rows).anySatisfy(row -> assertThat(row).containsEntry("cache_write_tokens", 1180)
                .containsEntry("completion_tokens", 1024));
        // Only the usable answer is replayable (ADR 0015): the malformed attempt gets no key.
        assertThat(rows).extracting(row -> row.get("replay_key") != null).containsExactlyInAnyOrder(false, true);
    }

    @Test
    void malformed_answer_twice_falls_back_to_invalid_output_and_flags_the_last_call() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("classification-malformed.json"))));

        ClassifierResult result = classifier.classify(item);

        assertThat(result).isEqualTo(new ClassifierResult.Failed(UnclassifiedReason.INVALID_OUTPUT));
        WIREMOCK.verify(2, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).extracting(row -> row.get("is_fallback")).containsExactlyInAnyOrder(false, true);
        assertThat(logRows()).extracting(row -> row.get("replay_key")).containsOnlyNulls();
    }

    @Test
    void ticker_the_item_does_not_have_is_invalid_output() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES))
                .willReturn(okJson(fixture("classification-unknown-ticker.json"))));

        assertThat(classifier.classify(item)).isEqualTo(new ClassifierResult.Failed(UnclassifiedReason.INVALID_OUTPUT));
    }

    @Test
    void timeout_is_a_provider_error_without_a_re_ask() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES))
                .willReturn(okJson(fixture("classification-valid.json")).withFixedDelay(4_000)));

        ClassifierResult result = classifier.classify(item);

        assertThat(result).isEqualTo(new ClassifierResult.Failed(UnclassifiedReason.PROVIDER_ERROR));
        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("response", null)
                .containsEntry("is_fallback", true)
                .containsEntry("model", "claude-haiku-5-5"));
    }

    @Test
    void server_error_is_a_provider_error() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(aResponse().withStatus(529)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}")));

        assertThat(classifier.classify(item)).isEqualTo(new ClassifierResult.Failed(UnclassifiedReason.PROVIDER_ERROR));
    }

    @Test
    void used_up_budget_defers_without_any_call() {
        jdbc.update("""
                INSERT INTO ai_daily_usage (usage_date, feature, call_count, cost_usd) VALUES (?, ?, 1, 1.00)
                """, LocalDate.now(ZoneOffset.UTC), "news-classification");

        assertThat(classifier.classify(item)).isInstanceOf(ClassifierResult.Deferred.class);
        WIREMOCK.verify(0, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).isEmpty();
    }

    @Test
    void recorded_answer_is_replayed_for_the_same_input_without_a_call_a_log_row_or_usage() {
        WIREMOCK.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(fixture("classification-valid.json"))));
        ClassifierResult live = classifier.classify(item);
        NewsItem sameContent = new NewsItem(UUID.randomUUID(), item.headline(), item.summary(),
                "https://example.com/apple-syndicated", item.publishedAt(), item.source(), item.tickers(),
                NewsStatus.RECEIVED, Optional.empty());

        ClassifierResult replayed = classifier.classify(sameContent);

        assertThat(replayed).isEqualTo(live);
        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(logRows()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT call_count FROM ai_daily_usage WHERE feature = ? AND usage_date = ?",
                Integer.class, "news-classification", LocalDate.now(ZoneOffset.UTC))).isEqualTo(1);
    }
}
