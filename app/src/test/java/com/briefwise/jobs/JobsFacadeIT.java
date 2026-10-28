package com.briefwise.jobs;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.jobs.JobsFacade.JobRunView;
import com.briefwise.jobs.JobsFacade.JobStepView;
import com.briefwise.jobs.domain.model.JobStatus;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.news.RecordingNewsClassifier;
import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The job runner's {@code news-ingest} end to end (DESIGN 7.1): Finnhub on WireMock → outbox → Testcontainers Kafka
 * → consumer → classifier fake, with the run waiting for consumer lag 0 before it sweeps (ADR 0016).
 */
class JobsFacadeIT extends AbstractIntegrationTest {

    private static final String PATH = "/company-news";

    @Autowired
    JobsFacade jobs;

    @Autowired
    RecordingNewsClassifier classifier;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM news_outbox");
        jdbc.update("DELETE FROM news_items");
        classifier.reset();
        classifier.answer(item -> new ClassifierResult.Classified(new NewsClassification(Sentiment.NEUTRAL,
                Relevance.LOW, EventType.OTHER, item.tickers(), "Synthetic test result.", "test-v1")));
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).atPriority(10).willReturn(okJson("[]")));
    }

    @Test
    void runner_ingests_publishes_waits_until_consumed_and_sweeps() {
        WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("symbol", equalTo("AAPL")).atPriority(1)
                .willReturn(okJson("""
                        [{"category":"company","datetime":%d,"headline":"Apple opens a synthetic campus","id":1,
                          "image":"","related":"","source":"Synthetic Wire","summary":"Synthetic test article.",
                          "url":"https://news.example.com/jobs-it-campus"}]"""
                        .formatted(Instant.now().minusSeconds(3600).getEpochSecond()))));

        JobRunView run = jobs.run("news-ingest", JobTrigger.RUNNER);

        assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(run.steps()).extracting(JobStepView::name).containsExactly("ingest", "publish", "drain", "sweep");
        assertThat(run.steps().getFirst().result()).containsEntry("inserted", 1);
        // drain returned only after the consumer committed, so the item is already classified
        assertThat(jdbc.queryForObject("SELECT status FROM news_items WHERE url = ?", String.class,
                "https://news.example.com/jobs-it-campus")).isEqualTo("CLASSIFIED");
        assertThat(run.steps().getLast().result()).containsEntry("picked", 0);   // too young for the sweep
    }
}
