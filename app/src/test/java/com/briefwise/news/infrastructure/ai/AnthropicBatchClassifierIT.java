package com.briefwise.news.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.domain.model.BatchOutcome;
import com.briefwise.news.domain.model.EvalLabels;
import com.briefwise.news.domain.model.EvalPrediction;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The Message Batches adapter against WireMock standing in for the Anthropic API (never a live call; ADR 0018). Covers
 * the request shape (same prompt, schema, effort and cache marker as production), polling until {@code ended},
 * valid / malformed / errored / expired results, logging and usage at the batch price, and the refusals (few-shot
 * leakage, budget) and the unfinished batch. Batch JSON follows the Messages Batches API reference.
 */
class AnthropicBatchClassifierIT extends AbstractIntegrationTest {

    private static final String BATCHES = "/v1/messages/batches";
    private static final String BATCH_ID = "msgbatch_test";
    private static final String FEATURE = "news-classification-eval";

    @Autowired
    AnthropicBatchClassifier classifier;

    @Autowired
    JdbcTemplate jdbc;

    private final GoldenCase valid = golden("Apple beats quarterly revenue estimates", "AAPL");
    private final GoldenCase malformed = golden("Microsoft names a new CFO", "MSFT");
    private final GoldenCase errored = golden("Exxon slips with crude", "XOM");
    private final GoldenCase expired = golden("JPMorgan opens new branches", "JPM");

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        jdbc.update("DELETE FROM ai_generation_log");
        jdbc.update("DELETE FROM ai_daily_usage");
    }

    private static GoldenCase golden(String headline, String ticker) {
        return new GoldenCase(UUID.randomUUID(), true, Set.of(new Ticker(ticker)), "Example Wire", headline,
                "Summary.", new EvalLabels(Sentiment.NEUTRAL, Relevance.LOW, EventType.OTHER));
    }

    private static String batch(String status) {
        String resultsUrl = "ended".equals(status)
                ? "\"" + WIREMOCK.baseUrl() + BATCHES + "/" + BATCH_ID + "/results\"" : "null";
        return """
                {"id": "%s", "type": "message_batch", "processing_status": "%s",
                 "request_counts": {"processing": 0, "succeeded": 2, "errored": 1, "canceled": 0, "expired": 1},
                 "created_at": "2026-10-28T08:00:00Z", "expires_at": "2026-10-29T08:00:00Z",
                 "ended_at": %s, "archived_at": null, "cancel_initiated_at": null, "results_url": %s}
                """.formatted(BATCH_ID, status, "ended".equals(status) ? "\"2026-10-28T08:03:00Z\"" : "null",
                resultsUrl).replace("\n", " ");
    }

    private static String succeeded(GoldenCase c, String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"custom_id": "%s", "result": {"type": "succeeded", "message": {"id": "msg_%s", "type": "message", \
                "role": "assistant", "model": "claude-haiku-5-5", "content": [{"type": "text", "text": "%s"}], \
                "stop_reason": "end_turn", "stop_sequence": null, "usage": {"input_tokens": 90, "output_tokens": 60, \
                "cache_creation_input_tokens": 0, "cache_read_input_tokens": 1000}}}}""".formatted(c.id(), c.id(),
                escaped);
    }

    private void stubBatch(String results) {
        WIREMOCK.stubFor(post(urlPathEqualTo(BATCHES)).willReturn(okJson(batch("in_progress"))));
        WIREMOCK.stubFor(get(urlPathEqualTo(BATCHES + "/" + BATCH_ID)).inScenario("poll").whenScenarioStateIs(STARTED)
                .willReturn(okJson(batch("in_progress"))).willSetStateTo("ended"));
        WIREMOCK.stubFor(get(urlPathEqualTo(BATCHES + "/" + BATCH_ID)).inScenario("poll").whenScenarioStateIs("ended")
                .willReturn(okJson(batch("ended"))));
        WIREMOCK.stubFor(get(urlPathMatching(BATCHES + "/" + BATCH_ID + "/results"))
                .willReturn(aResponse().withHeader("Content-Type", "application/binary").withBody(results)));
    }

    @Test
    void every_result_kind_becomes_a_prediction_and_is_logged_at_the_batch_price() {
        String results = String.join("\n",
                succeeded(valid, "{\"rationale\": \"Revenue beat.\", \"sentiment\": \"Positive\", "
                        + "\"relevance\": \"HIGH\", \"eventType\": \"EARNINGS\", \"affectedTickers\": [\"AAPL\"]}"),
                succeeded(malformed, "I think this is neutral."),
                "{\"custom_id\": \"" + errored.id() + "\", \"result\": {\"type\": \"errored\", \"error\": "
                        + "{\"type\": \"error\", \"error\": {\"type\": \"invalid_request_error\", "
                        + "\"message\": \"bad\"}}}}",
                "{\"custom_id\": \"" + expired.id() + "\", \"result\": {\"type\": \"expired\"}}") + "\n";
        stubBatch(results);

        BatchOutcome outcome = classifier.classify(List.of(valid, malformed, errored, expired));

        assertThat(outcome).isInstanceOf(BatchOutcome.Completed.class);
        BatchOutcome.Completed completed = (BatchOutcome.Completed) outcome;
        assertThat(completed.batchId()).isEqualTo(BATCH_ID);
        assertThat(completed.model()).isEqualTo("claude-haiku-5-5");
        Map<UUID, EvalPrediction> byCase = completed.predictions().stream()
                .collect(Collectors.toMap(EvalPrediction::caseId, Function.identity()));
        assertThat(byCase).hasSize(4);
        assertThat(byCase.get(valid.id()).labels())
                .contains(new EvalLabels(Sentiment.POSITIVE, Relevance.HIGH, EventType.EARNINGS));
        assertThat(byCase.get(malformed.id()).valid()).isFalse();
        assertThat(byCase.get(errored.id()).valid()).isFalse();
        assertThat(byCase.get(expired.id()).valid()).isFalse();
        // two answers of 90 x 0.10 + 60 x 0.50 + 1,000 x 0.01 = 49 per 1M, half price in a batch
        assertThat(completed.costUsd()).isEqualByComparingTo("0.000049");

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ai_generation_log");
        assertThat(rows).hasSize(4).allSatisfy(row -> assertThat(row).containsEntry("feature", FEATURE));
        assertThat(rows).filteredOn(row -> valid.id().equals(row.get("subject_id"))).singleElement()
                .satisfies(row -> assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.0000245"));
        assertThat(jdbc.queryForObject("SELECT call_count FROM ai_daily_usage WHERE feature = ? AND usage_date = ?",
                Integer.class, FEATURE, LocalDate.now(ZoneOffset.UTC))).isEqualTo(4);

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(BATCHES))
                .withRequestBody(matchingJsonPath("$.requests.length()", equalTo("4")))
                .withRequestBody(matchingJsonPath("$.requests[0].custom_id", equalTo(valid.id().toString())))
                .withRequestBody(matchingJsonPath("$.requests[0].params.model", equalTo("claude-haiku-5-5")))
                .withRequestBody(matchingJsonPath("$.requests[0].params.max_tokens", equalTo("1024")))
                .withRequestBody(matchingJsonPath("$.requests[0].params.system[0].cache_control.type",
                        equalTo("ephemeral")))
                .withRequestBody(matchingJsonPath("$.requests[0].params.output_config.effort", equalTo("low")))
                .withRequestBody(matchingJsonPath("$.requests[0].params.output_config.format.type",
                        equalTo("json_schema")))
                .withRequestBody(matchingJsonPath(
                        "$.requests[0].params.output_config.format.schema.properties.eventType"))
                .withRequestBody(matchingJsonPath("$.requests[0].params.messages[0].content",
                        containing("headline: Apple beats quarterly revenue estimates"))));
    }

    @Test
    void a_golden_headline_in_the_few_shot_examples_is_refused_before_anything_is_sent() {
        GoldenCase leaked = golden("Coca-Cola tops quarterly revenue estimates as pricing offsets softer volumes",
                "KO");

        BatchOutcome outcome = classifier.classify(List.of(valid, leaked));

        assertThat(outcome).isInstanceOf(BatchOutcome.Refused.class);
        assertThat(((BatchOutcome.Refused) outcome).reason()).contains("few-shot");
        assertThat(WIREMOCK.getAllServeEvents()).isEmpty();
    }

    @Test
    void a_worst_case_estimate_above_the_remaining_budget_is_refused() {
        jdbc.update("INSERT INTO ai_daily_usage (usage_date, feature, call_count, cost_usd) VALUES (?, ?, 1, ?)",
                LocalDate.now(ZoneOffset.UTC), FEATURE, new BigDecimal("0.49999"));

        BatchOutcome outcome = classifier.classify(List.of(valid, malformed));

        assertThat(outcome).isInstanceOf(BatchOutcome.Refused.class);
        assertThat(((BatchOutcome.Refused) outcome).reason()).contains("exceeds the remaining");
        assertThat(WIREMOCK.getAllServeEvents()).isEmpty();
    }

    @Test
    void a_batch_that_does_not_end_within_max_wait_is_incomplete_with_its_id() {
        WIREMOCK.stubFor(post(urlPathEqualTo(BATCHES)).willReturn(okJson(batch("in_progress"))));
        WIREMOCK.stubFor(get(urlPathEqualTo(BATCHES + "/" + BATCH_ID)).willReturn(okJson(batch("in_progress"))));

        BatchOutcome outcome = classifier.classify(List.of(valid));

        assertThat(outcome).isEqualTo(new BatchOutcome.Incomplete(BATCH_ID,
                "not ended after PT2S; fetch the results later with this batch id"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_generation_log", Integer.class)).isZero();
    }

    @Test
    void unknown_custom_ids_in_the_results_are_ignored() {
        stubBatch(succeeded(golden("Not in this run", "AAPL"), "{}") + "\n");

        BatchOutcome outcome = classifier.classify(List.of(valid));

        assertThat(((BatchOutcome.Completed) outcome).predictions()).isEmpty();
        assertThat(Optional.ofNullable(jdbc.queryForObject("SELECT count(*) FROM ai_generation_log",
                Integer.class))).contains(0);
    }
}
