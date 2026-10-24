package com.briefwise.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.NewsFacade.PublishSummaryView;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.Ticker;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * {@code news.raw} end to end on a real broker (Testcontainers Kafka): outbox relay, idempotent consumer (K3),
 * retries and the dead-letter topic (ADR 0011). The classifier is the recording fake; no LLM is involved.
 */
class NewsKafkaIT extends AbstractIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final String TICKER = "AAPL";

    @Autowired
    NewsFacade facade;

    @Autowired
    RecordingNewsClassifier classifier;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ConsumerFactory<Object, Object> consumers;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM news_outbox");
        jdbc.update("DELETE FROM news_items");
        classifier.reset();
        classifier.answer(item -> Optional.of(classification(item)));
    }

    private static NewsClassification classification(NewsItem item) {
        return new NewsClassification(Sentiment.NEUTRAL, Relevance.LOW, EventType.OTHER, item.tickers(),
                "Synthetic test result.", "test-v1");
    }

    private UUID storeReceived(String headline) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO news_items (id, headline, url, url_hash, source, published_at, status)
                VALUES (?, ?, ?, ?, 'Synthetic Wire', now(), 'RECEIVED')""",
                id, headline, "https://news.example.com/" + id, id.toString().replace("-", ""));
        jdbc.update("INSERT INTO news_item_tickers (news_item_id, ticker) VALUES (?, ?)", id, TICKER);
        return id;
    }

    private String status(UUID newsId) {
        return jdbc.queryForObject("SELECT status FROM news_items WHERE id = ?", String.class, newsId);
    }

    private static String message(UUID newsId) {
        return """
                {"messageId":"%s","newsId":"%s","occurredAt":"2026-10-24T07:00:00Z"}"""
                .formatted(UUID.randomUUID(), newsId);
    }

    private void send(String value) throws Exception {
        kafka.send("news.raw", TICKER, value).get();
    }

    private long calls(UUID newsId) {
        return classifier.calls().stream().filter(item -> item.id().equals(newsId)).count();
    }

    /** Reads {@code topic} from the beginning with a fresh consumer group until a record matches. */
    private ConsumerRecord<Object, Object> awaitRecord(String topic, Predicate<ConsumerRecord<Object, Object>> match) {
        try (Consumer<Object, Object> consumer = consumers.createConsumer("it-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<Object, Object>> found = new ArrayList<>();
            await().atMost(WAIT).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (match.test(r)) {
                        found.add(r);
                    }
                });
                return !found.isEmpty();
            });
            return found.getFirst();
        }
    }

    private static String header(ConsumerRecord<Object, Object> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    void relayed_item_is_consumed_and_classified() {
        UUID newsId = storeReceived("Apple opens new campus");
        jdbc.update("""
                INSERT INTO news_outbox (id, aggregate_id, topic, message_key, payload, correlation_id)
                VALUES (?, ?, 'news.raw', ?, ?::jsonb, 'it-correlation')""",
                UUID.randomUUID(), newsId, TICKER, message(newsId));

        PublishSummaryView summary = facade.publishPending();

        assertThat(summary.published()).isEqualTo(1);
        assertThat(summary.pending()).isZero();
        await().atMost(WAIT).until(() -> "CLASSIFIED".equals(status(newsId)));
        assertThat(classifier.calls()).singleElement()
                .satisfies(item -> assertThat(item.tickers()).containsExactly(new Ticker(TICKER)));
        ConsumerRecord<Object, Object> sent = awaitRecord("news.raw",
                r -> String.valueOf(r.value()).contains(newsId.toString()));
        assertThat(sent.key()).isEqualTo(TICKER);
        assertThat(header(sent, "X-Correlation-ID")).isEqualTo("it-correlation");
    }

    @Test
    void duplicate_delivery_classifies_once() throws Exception {
        UUID newsId = storeReceived("Apple beats estimates");
        UUID marker = storeReceived("Apple marker item");

        send(message(newsId));
        send(message(newsId)); // e.g. the relay re-sent after a crash before marking the row published
        send(message(marker));

        // Same key, same partition, so the marker is processed after both copies.
        await().atMost(WAIT).until(() -> "CLASSIFIED".equals(status(marker)));
        assertThat(status(newsId)).isEqualTo("CLASSIFIED");
        assertThat(calls(newsId)).isEqualTo(1);
    }

    @Test
    void failing_item_is_retried_then_sent_to_the_dead_letter_topic() throws Exception {
        UUID newsId = storeReceived("Apple item the classifier cannot handle");
        classifier.answer(item -> {
            throw new IllegalStateException("classifier failed");
        });

        send(message(newsId));

        ConsumerRecord<Object, Object> dead = awaitRecord("news.raw.DLT",
                r -> String.valueOf(r.value()).contains(newsId.toString()));
        assertThat(calls(newsId)).isEqualTo(3); // max-attempts
        assertThat(status(newsId)).isEqualTo("RECEIVED");
        assertThat(dead.key()).isEqualTo(TICKER);
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void malformed_message_goes_to_the_dead_letter_topic_without_retries() throws Exception {
        String poison = "not json " + UUID.randomUUID();

        send(poison);

        ConsumerRecord<Object, Object> dead = awaitRecord("news.raw.DLT", r -> poison.equals(r.value()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).endsWith("MalformedNewsMessageException");
        assertThat(classifier.calls()).isEmpty();
    }

    @Test
    void message_for_an_unknown_item_goes_to_the_dead_letter_topic_without_retries() throws Exception {
        UUID unknown = UUID.randomUUID();

        send(message(unknown));

        ConsumerRecord<Object, Object> dead = awaitRecord("news.raw.DLT",
                r -> String.valueOf(r.value()).contains(unknown.toString()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).endsWith("NewsItemNotFoundException");
    }
}
