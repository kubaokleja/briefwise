package com.briefwise.news.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
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
 * classification columns and constraints.
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
}
