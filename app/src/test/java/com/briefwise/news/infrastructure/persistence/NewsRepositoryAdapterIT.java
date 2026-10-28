package com.briefwise.news.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The near-duplicate query against real pg_trgm. Headline pairs and their similarity scores are the examples
 * used to choose D1 (ADR 0009): 0.6 within 48h, same ticker. Also the conditional status writes and the V12
 * classification columns and constraints, and the sweep queries (V14, ADR 0016).
 */
class NewsRepositoryAdapterIT extends AbstractIntegrationTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final DedupPolicy D1 = new DedupPolicy(0.6, Duration.ofHours(48));
    private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    NewsRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM news_items");
    }

    private UUID store(Ticker ticker, String headline, Instant publishedAt, Optional<UUID> duplicateOf) {
        String url = "https://news.example.com/" + UUID.randomUUID();
        NewsItem item = NewsItem.fetched(UUID.randomUUID(),
                new FetchedNews(ticker, headline, "", url, publishedAt, "Synthetic Wire"), duplicateOf);
        repository.insert(item, UrlHash.of(url));
        return item.id();
    }

    private Optional<UUID> nearDuplicateOf(Ticker ticker, String headline, Instant publishedAt) {
        return repository.findNearDuplicate(ticker, headline, publishedAt, D1);
    }

    @Test
    void suffix_variant_is_a_near_duplicate() { // similarity 0.81
        UUID original = store(AAPL, "Apple beats Q3 estimates as iPhone sales rise", T0, Optional.empty());

        assertThat(nearDuplicateOf(AAPL, "Apple beats Q3 estimates as iPhone sales rise, shares up 3%",
                T0.plus(Duration.ofHours(2)))).contains(original);
    }

    @Test
    void different_story_with_similar_wording_is_not() { // similarity 0.43
        store(AAPL, "Nvidia shares fall 5% after export restrictions", T0, Optional.empty());

        assertThat(nearDuplicateOf(AAPL, "Nvidia shares fall 3% after earnings miss", T0)).isEmpty();
    }

    @Test
    void same_wording_outside_the_window_is_not() { // similarity 0.88, three days apart (recurring earnings)
        store(AAPL, "Exxon Mobil reports Q2 earnings", T0, Optional.empty());

        assertThat(nearDuplicateOf(AAPL, "Exxon Mobil reports Q3 earnings", T0.plus(Duration.ofHours(72)))).isEmpty();
        assertThat(nearDuplicateOf(AAPL, "Exxon Mobil reports Q3 earnings", T0.minus(Duration.ofHours(47)))).isPresent();
    }

    @Test
    void other_tickers_are_not_compared() {
        store(MSFT, "Apple beats Q3 estimates as iPhone sales rise", T0, Optional.empty());

        assertThat(nearDuplicateOf(AAPL, "Apple beats Q3 estimates as iPhone sales rise", T0)).isEmpty();
    }

    @Test
    void a_near_duplicate_is_never_chosen_as_the_original() {
        UUID original = store(AAPL, "JPMorgan raises dividend to $1.40 per share", T0, Optional.empty());
        store(AAPL, "JPMorgan raises dividend to $1.40 per share, stock up", T0, Optional.of(original));

        assertThat(nearDuplicateOf(AAPL, "JPMorgan raises dividend to $1.40 per share, stock up",
                T0.plus(Duration.ofHours(1)))).contains(original);
    }

    @Test
    void url_hash_lookup_and_idempotent_ticker_merge() {
        String url = "https://news.example.com/shared";
        NewsItem item = NewsItem.fetched(UUID.randomUUID(),
                new FetchedNews(AAPL, "Apple and Microsoft sign deal", "", url, T0, "Synthetic Wire"), Optional.empty());
        repository.insert(item, UrlHash.of(url));

        assertThat(repository.findIdByUrlHash(UrlHash.of(url))).contains(item.id());
        repository.addTicker(item.id(), MSFT);
        repository.addTicker(item.id(), MSFT);

        List<String> tickers = jdbc.queryForList(
                "SELECT ticker FROM news_item_tickers WHERE news_item_id = ? ORDER BY ticker", String.class, item.id());
        assertThat(tickers).containsExactly("AAPL", "MSFT");
    }

    @Test
    void stored_item_reads_back_with_its_current_tickers() {
        UUID original = store(AAPL, "Apple beats Q3 estimates as iPhone sales rise", T0, Optional.empty());
        UUID duplicate = store(AAPL, "Apple beats Q3 estimates as iPhone sales rise, shares up 3%", T0,
                Optional.of(original));
        repository.addTicker(original, MSFT);

        NewsItem read = repository.findById(original).orElseThrow();

        assertThat(read.tickers()).containsExactlyInAnyOrder(AAPL, MSFT);
        assertThat(read.status()).isEqualTo(NewsStatus.RECEIVED);
        assertThat(read.duplicateOf()).isEmpty();
        assertThat(repository.findById(duplicate).orElseThrow().duplicateOf()).contains(original);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void only_the_first_write_from_received_succeeds() {
        UUID id = store(AAPL, "Apple opens new campus", T0, Optional.empty());

        assertThat(repository.skipFromReceived(id)).isTrue();
        assertThat(repository.classifyFromReceived(id, classification(Set.of(AAPL)), T0)).isFalse();
        assertThat(repository.markUnclassifiedFromReceived(id, UnclassifiedReason.PROVIDER_ERROR, T0)).isFalse();
        assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(NewsStatus.SKIPPED);
    }

    private static NewsClassification classification(Set<Ticker> affected) {
        return new NewsClassification(Sentiment.NEGATIVE, Relevance.HIGH, EventType.ANALYST, affected,
                "Broker downgrade.", "v1");
    }

    @Test
    void classification_stores_the_labels_and_marks_only_the_affected_tickers() {
        UUID id = store(AAPL, "Broker downgrades Apple; Microsoft mentioned", T0, Optional.empty());
        repository.addTicker(id, MSFT);

        assertThat(repository.classifyFromReceived(id, classification(Set.of(AAPL)), T0.plusSeconds(60))).isTrue();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM news_items WHERE id = ?", id);
        assertThat(row).containsEntry("status", "CLASSIFIED")
                .containsEntry("sentiment", "NEGATIVE")
                .containsEntry("relevance", "HIGH")
                .containsEntry("event_type", "ANALYST")
                .containsEntry("rationale", "Broker downgrade.")
                .containsEntry("prompt_version", "v1")
                .containsEntry("unclassified_reason", null);
        assertThat(((Timestamp) row.get("classified_at")).toInstant()).isEqualTo(T0.plusSeconds(60));
        assertThat(jdbc.queryForList("SELECT ticker FROM news_item_tickers WHERE news_item_id = ? AND affected",
                String.class, id)).containsExactly("AAPL");
    }

    @Test
    void unclassified_item_keeps_its_reason_and_no_labels() {
        UUID id = store(AAPL, "Apple headline with an injection", T0, Optional.empty());

        assertThat(repository.markUnclassifiedFromReceived(id, UnclassifiedReason.INJECTION_SUSPECTED, T0)).isTrue();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM news_items WHERE id = ?", id);
        assertThat(row).containsEntry("status", "UNCLASSIFIED")
                .containsEntry("unclassified_reason", "INJECTION_SUSPECTED")
                .containsEntry("sentiment", null);
    }

    @Test
    void schema_rejects_a_classified_item_without_labels() {
        UUID id = store(AAPL, "Apple opens new campus", T0, Optional.empty());

        assertThatThrownBy(() -> jdbc.update("UPDATE news_items SET status = 'CLASSIFIED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE news_items SET status = 'UNCLASSIFIED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- sweep (V14, ADR 0016). created_at is the database clock, so ages are set relative to now().

    private UUID storeAged(Duration age) {
        UUID id = store(AAPL, "Apple item " + UUID.randomUUID(), T0, Optional.empty());
        jdbc.update("UPDATE news_items SET created_at = now() - ? * interval '1 second' WHERE id = ?",
                age.toSeconds(), id);
        return id;
    }

    private void outboxRow(UUID newsId, boolean published) {
        jdbc.update("""
                INSERT INTO news_outbox (id, aggregate_id, topic, message_key, payload, published_at)
                VALUES (?, ?, 'news.raw', 'AAPL', '{}'::jsonb, CASE WHEN ? THEN now() END)""",
                UUID.randomUUID(), newsId, published);
    }

    private Instant tenMinutesAgo() {
        return jdbc.queryForObject("SELECT now() - interval '10 minutes'", Timestamp.class).toInstant();
    }

    @Test
    void sweep_picks_old_received_items_with_a_published_or_no_outbox_row_oldest_first() {
        UUID oldest = storeAged(Duration.ofHours(3));
        outboxRow(oldest, true);
        UUID noOutbox = storeAged(Duration.ofHours(2));
        UUID stillPending = storeAged(Duration.ofHours(1));   // the relay's job, not the sweep's
        outboxRow(stillPending, false);
        UUID young = storeAged(Duration.ofMinutes(1));         // a consumer may still be working on it
        outboxRow(young, true);
        UUID classified = storeAged(Duration.ofHours(4));
        repository.markUnclassifiedFromReceived(classified, UnclassifiedReason.INVALID_OUTPUT, T0);

        assertThat(repository.findSweepable(tenMinutesAgo(), 10)).containsExactly(oldest, noOutbox);
        assertThat(repository.findSweepable(tenMinutesAgo(), 1)).containsExactly(oldest);
    }

    private void unclassified(UUID id, UnclassifiedReason reason, Instant classifiedAt) {
        repository.markUnclassifiedFromReceived(id, reason, classifiedAt);
    }

    @Test
    void only_recent_provider_errors_under_the_cap_are_reopened() {
        Instant now = jdbc.queryForObject("SELECT now()", Timestamp.class).toInstant();
        UUID recent = storeAged(Duration.ofHours(1));
        unclassified(recent, UnclassifiedReason.PROVIDER_ERROR, now.minus(Duration.ofHours(1)));
        UUID stale = storeAged(Duration.ofDays(3));
        unclassified(stale, UnclassifiedReason.PROVIDER_ERROR, now.minus(Duration.ofDays(3)));
        UUID exhausted = storeAged(Duration.ofHours(1));
        unclassified(exhausted, UnclassifiedReason.PROVIDER_ERROR, now.minus(Duration.ofHours(1)));
        jdbc.update("UPDATE news_items SET sweep_attempts = 3 WHERE id = ?", exhausted);
        UUID invalid = storeAged(Duration.ofHours(1));
        unclassified(invalid, UnclassifiedReason.INVALID_OUTPUT, now.minus(Duration.ofHours(1)));

        int reopened = repository.reopenProviderErrors(tenMinutesAgo(), now.minus(Duration.ofHours(48)), 3);

        assertThat(reopened).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM news_items WHERE id = ?", recent);
        assertThat(row).containsEntry("status", "RECEIVED").containsEntry("unclassified_reason", null)
                .containsEntry("classified_at", null);
        assertThat(jdbc.queryForList("SELECT status FROM news_items WHERE id IN (?, ?, ?)", String.class,
                stale, exhausted, invalid)).containsOnly("UNCLASSIFIED");
    }

    @Test
    void sweep_attempts_are_counted_and_the_give_up_reason_is_allowed() {
        UUID id = storeAged(Duration.ofHours(1));

        assertThat(repository.incrementSweepAttempts(id)).isEqualTo(1);
        assertThat(repository.incrementSweepAttempts(id)).isEqualTo(2);
        assertThat(repository.markUnclassifiedFromReceived(id, UnclassifiedReason.RETRIES_EXHAUSTED, T0)).isTrue();
        assertThat(jdbc.queryForObject("SELECT unclassified_reason FROM news_items WHERE id = ?", String.class, id))
                .isEqualTo("RETRIES_EXHAUSTED");
        assertThatThrownBy(() -> repository.incrementSweepAttempts(UUID.randomUUID()))
                .isInstanceOf(NewsItemNotFoundException.class);
    }

    private UUID classified(String headline, Instant publishedAt, Relevance relevance, Set<Ticker> affected,
                            Ticker... alsoFetchedFor) {
        UUID id = store(affected.iterator().next(), headline, publishedAt, Optional.empty());
        for (Ticker t : affected) {
            repository.addTicker(id, t);
        }
        for (Ticker t : alsoFetchedFor) {
            repository.addTicker(id, t);
        }
        repository.classifyFromReceived(id, new NewsClassification(Sentiment.POSITIVE, relevance, EventType.PRODUCT,
                affected, "Synthetic rationale.", "v1"), publishedAt.plusSeconds(300));
        return id;
    }

    @Test
    void selection_reads_classified_items_of_held_affected_tickers_within_the_window() {
        Ticker nvda = new Ticker("NVDA");
        UUID wanted = classified("Apple and Microsoft sign a deal", T0, Relevance.HIGH, Set.of(AAPL, MSFT));
        classified("Microsoft only fetched for Apple", T0, Relevance.HIGH, Set.of(MSFT), AAPL); // AAPL not affected
        classified("Too old", T0.minus(Duration.ofHours(30)), Relevance.HIGH, Set.of(AAPL));
        classified("Listicle", T0, Relevance.LOW, Set.of(AAPL));
        classified("Not held", T0, Relevance.HIGH, Set.of(nvda));
        store(AAPL, "Still received", T0, Optional.empty());

        List<ClassifiedNews> found = repository.findClassified(Set.of(AAPL), T0.minus(Duration.ofHours(24)),
                Set.of(Relevance.MEDIUM, Relevance.HIGH), 50);

        assertThat(found).singleElement().satisfies(n -> {
            assertThat(n.id()).isEqualTo(wanted);
            assertThat(n.affectedTickers()).containsExactlyInAnyOrder(AAPL, MSFT);
            assertThat(n.relevance()).isEqualTo(Relevance.HIGH);
            assertThat(n.eventType()).isEqualTo(EventType.PRODUCT);
            assertThat(n.rationale()).isEqualTo("Synthetic rationale.");
            assertThat(n.classifiedAt()).isEqualTo(T0.plusSeconds(300));
        });
    }

    @Test
    void selection_returns_the_newest_items_up_to_the_limit() {
        classified("Older", T0.minusSeconds(600), Relevance.MEDIUM, Set.of(AAPL));
        UUID newest = classified("Newest", T0, Relevance.MEDIUM, Set.of(AAPL));

        List<ClassifiedNews> found = repository.findClassified(Set.of(AAPL), T0.minus(Duration.ofHours(24)),
                Set.of(Relevance.MEDIUM), 1);

        assertThat(found).extracting(ClassifiedNews::id).containsExactly(newest);
    }
}
