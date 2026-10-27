package com.briefwise.news.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.aiops.AiopsFacade;
import com.briefwise.aiops.AiopsFacade.GenerationEntry;
import com.briefwise.aiops.AiopsFacade.ReplayRequest;
import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.TokenCount;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The {@code test} profile as CI runs it: {@code provider: replay}, {@code on-miss: fail} (ADR 0015). Proves a CI run
 * classifies from recordings with zero LLM HTTP requests, and that a missing recording defers the item instead of
 * calling the model or marking it {@code UNCLASSIFIED} for good.
 */
class ReplayNewsClassificationIT extends AbstractIntegrationTest {

    private static final String RECORDED = "{\"rationale\": \"Guidance raised on strong demand.\", "
            + "\"sentiment\": \"POSITIVE\", \"relevance\": \"HIGH\", \"eventType\": \"GUIDANCE\", "
            + "\"affectedTickers\": [\"MSFT\"]}";

    @Autowired
    LlmNewsClassifier classifier;

    @Autowired
    NewsClassificationPrompt prompt;

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

    private static NewsItem item(String headline) {
        return new NewsItem(UUID.randomUUID(), headline, "Synthetic test item.", "https://example.com/" + UUID.randomUUID(),
                Instant.parse("2026-10-27T06:00:00Z"), "Synthetic", Set.of(new Ticker("MSFT")), NewsStatus.RECEIVED,
                Optional.empty());
    }

    /** As a live call with {@code on-miss: fallback} would have recorded it; the configured model is the test model. */
    private void record(NewsItem item, String model) {
        String data = prompt.render(item);
        aiops.recordGeneration(new GenerationEntry("news-classification", model, prompt.version(), "recorded",
                Optional.of(RECORDED), new TokenCount(100, 50), Duration.ofMillis(800), false, Optional.empty(),
                Optional.of(new ReplayRequest("news-classification", model, prompt.version(), data))));
    }

    @Test
    void recorded_answer_is_replayed_without_an_http_call_a_new_log_row_or_usage() {
        NewsItem item = item("Microsoft raises full-year guidance");
        record(item, "claude-haiku-5-5");
        jdbc.update("DELETE FROM ai_daily_usage");

        ClassifierResult result = classifier.classify(item);

        assertThat(result).isEqualTo(new ClassifierResult.Classified(new NewsClassification(Sentiment.POSITIVE,
                Relevance.HIGH, EventType.GUIDANCE, Set.of(new Ticker("MSFT")), "Guidance raised on strong demand.",
                prompt.version())));
        WIREMOCK.verify(0, postRequestedFor(urlPathEqualTo("/v1/messages")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_generation_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_daily_usage", Integer.class)).isZero();
    }

    @Test
    void missing_recording_defers_the_item_and_never_calls_the_model() {
        ClassifierResult result = classifier.classify(item("Microsoft opens a new data centre"));

        assertThat(result).isEqualTo(new ClassifierResult.Deferred("no replay entry"));
        WIREMOCK.verify(0, postRequestedFor(urlPathEqualTo("/v1/messages")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_generation_log", Integer.class)).isZero();
    }

    @Test
    void a_recording_for_another_model_is_a_miss() {
        NewsItem item = item("Microsoft raises full-year guidance");
        record(item, "claude-sonnet-5-5");

        assertThat(classifier.classify(item)).isEqualTo(new ClassifierResult.Deferred("no replay entry"));
    }
}
