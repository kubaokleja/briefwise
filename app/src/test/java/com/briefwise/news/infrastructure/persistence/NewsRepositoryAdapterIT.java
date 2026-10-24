package com.briefwise.news.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The near-duplicate query against real pg_trgm. Headline pairs and their similarity scores are the examples
 * used to choose D1 (ADR 0009): 0.6 within 48h, same ticker.
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
    void only_the_first_transition_from_received_succeeds() {
        UUID id = store(AAPL, "Apple opens new campus", T0, Optional.empty());

        assertThat(repository.transitionFromReceived(id, NewsStatus.CLASSIFIED)).isTrue();
        assertThat(repository.transitionFromReceived(id, NewsStatus.UNCLASSIFIED)).isFalse();
        assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(NewsStatus.CLASSIFIED);
    }
}
