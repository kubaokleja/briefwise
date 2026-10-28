package com.briefwise.news;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.NewsFacade.ClassificationEvalView;
import com.briefwise.news.NewsFacade.EvalStatusView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileSystemUtils;

/**
 * {@link NewsFacade#evaluateClassification} end to end (ADR 0018): the synthetic fixture golden set
 * ({@code src/test/resources/eval/golden-fixture.csv}), the batch adapter against WireMock, scoring, the report files
 * under {@code target/eval-results} and the regression gate against a {@code baseline.json}.
 */
class NewsEvalFacadeIT extends AbstractIntegrationTest {

    private static final Path RESULTS = Path.of("target/eval-results");
    private static final String BATCHES = "/v1/messages/batches";

    @Autowired
    NewsFacade news;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        jdbc.update("DELETE FROM ai_generation_log");
        jdbc.update("DELETE FROM ai_daily_usage");
        FileSystemUtils.deleteRecursively(RESULTS.toFile());
    }

    /** Answers for the six fixture rows; the JNJ lawsuit is wrongly answered NEUTRAL, everything else is right. */
    private void stubBatchAnswers() {
        String batch = """
                {"id": "msgbatch_fixture", "type": "message_batch", "processing_status": "ended", \
                "request_counts": {"processing": 0, "succeeded": 6, "errored": 0, "canceled": 0, "expired": 0}, \
                "created_at": "2026-10-28T08:00:00Z", "expires_at": "2026-10-29T08:00:00Z", \
                "ended_at": "2026-10-28T08:02:00Z", "archived_at": null, "cancel_initiated_at": null, \
                "results_url": "%s/v1/messages/batches/msgbatch_fixture/results"}""".formatted(WIREMOCK.baseUrl());
        WIREMOCK.stubFor(post(urlPathEqualTo(BATCHES)).willReturn(okJson(batch)));
        WIREMOCK.stubFor(get(urlPathEqualTo(BATCHES + "/msgbatch_fixture")).willReturn(okJson(batch)));
        String results = String.join("\n", List.of(
                line(1, "POSITIVE", "HIGH", "EARNINGS", "AAPL"),
                line(2, "NEUTRAL", "LOW", "OTHER", "MSFT"),
                line(3, "NEUTRAL", "HIGH", "LITIGATION", "JNJ"),
                line(4, "NEUTRAL", "MEDIUM", "PRODUCT", "KO"),
                line(5, "NEUTRAL", "LOW", "MACRO", "XOM"),
                line(6, "POSITIVE", "MEDIUM", "ANALYST", "JPM"))) + "\n";
        WIREMOCK.stubFor(get(urlPathEqualTo(BATCHES + "/msgbatch_fixture/results"))
                .willReturn(aResponse().withHeader("Content-Type", "application/binary").withBody(results)));
    }

    private static String line(int row, String sentiment, String relevance, String eventType, String ticker) {
        String id = "0b5c6a51-000%d-4000-8000-00000000000%d".formatted(row, row);
        String answer = ("{\\\"rationale\\\": \\\"r\\\", \\\"sentiment\\\": \\\"%s\\\", \\\"relevance\\\": \\\"%s\\\", "
                + "\\\"eventType\\\": \\\"%s\\\", \\\"affectedTickers\\\": [\\\"%s\\\"]}")
                .formatted(sentiment, relevance, eventType, ticker);
        return """
                {"custom_id": "%s", "result": {"type": "succeeded", "message": {"id": "msg_%d", "type": "message", \
                "role": "assistant", "model": "claude-haiku-5-5", "content": [{"type": "text", "text": "%s"}], \
                "stop_reason": "end_turn", "stop_sequence": null, "usage": {"input_tokens": 90, \
                "output_tokens": 60, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 1000}}}}"""
                .formatted(id, row, answer);
    }

    @Test
    void first_run_without_a_baseline_passes_and_writes_the_reports() throws IOException {
        stubBatchAnswers();

        ClassificationEvalView view = news.evaluateClassification();

        assertThat(view.status()).isEqualTo(EvalStatusView.PASSED);
        assertThat(view.cases()).isEqualTo(6);
        assertThat(view.invalid()).isZero();
        assertThat(view.batchId()).contains("msgbatch_fixture");
        assertThat(view.baselineSentimentMacroF1()).isEmpty();
        // NEGATIVE: never predicted -> F1 0; NEUTRAL: P 3/4 R 1 -> 6/7; POSITIVE: 1 -> macro (0 + 6/7 + 1) / 3
        assertThat(view.sentimentMacroF1().orElseThrow()).isCloseTo((6.0 / 7 + 1) / 3, within(1e-9));
        assertThat(RESULTS.resolve("latest.json")).exists();
        assertThat(Files.readString(RESULTS.resolve("latest.md"), StandardCharsets.UTF_8))
                .contains("**Gate: PASSED**").contains("6 golden cases (3 random)");
    }

    @Test
    void a_drop_of_more_than_three_points_below_the_baseline_fails() throws IOException {
        stubBatchAnswers();
        Files.createDirectories(RESULTS);
        Files.writeString(RESULTS.resolve("baseline.json"), """
                {"model": "claude-haiku-5-5", "promptVersion": "v1", "all": {"sentiment": {"macroF1": 0.95}}}
                """, StandardCharsets.UTF_8);

        ClassificationEvalView view = news.evaluateClassification();

        assertThat(view.status()).isEqualTo(EvalStatusView.REGRESSED);
        assertThat(view.passed()).isFalse();
        assertThat(view.baselineSentimentMacroF1()).contains(0.95);
        assertThat(view.message()).contains("baseline 0.950");
    }
}
