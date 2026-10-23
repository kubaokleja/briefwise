package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsItemTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private final FetchedNews fetched = new FetchedNews(AAPL, " Apple beats estimates ", null,
            "https://example.com/a", Instant.parse("2026-10-20T12:00:00Z"), "Reuters");

    @Test
    void fetched_news_is_trimmed_and_summary_defaults_to_empty() {
        assertThat(fetched.headline()).isEqualTo("Apple beats estimates");
        assertThat(fetched.summary()).isEmpty();
    }

    @Test
    void fetched_news_requires_headline_url_and_source() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new FetchedNews(AAPL, " ", "", "https://example.com/a", now, "Reuters"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FetchedNews(AAPL, "h", "", "not a url", now, "Reuters"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FetchedNews(AAPL, "h", "", "https://example.com/a", now, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void new_item_is_received_unless_it_duplicates_an_original() {
        UUID original = UUID.randomUUID();

        NewsItem fresh = NewsItem.fetched(UUID.randomUUID(), fetched, Optional.empty());
        NewsItem duplicate = NewsItem.fetched(UUID.randomUUID(), fetched, Optional.of(original));

        assertThat(fresh.status()).isEqualTo(NewsStatus.RECEIVED);
        assertThat(fresh.tickers()).containsExactly(AAPL);
        assertThat(duplicate.status()).isEqualTo(NewsStatus.SKIPPED);
        assertThat(duplicate.duplicateOf()).contains(original);
    }

    @Test
    void a_near_duplicate_must_be_skipped() {
        assertThatThrownBy(() -> new NewsItem(UUID.randomUUID(), "h", "", "https://example.com/a", Instant.now(),
                "Reuters", Set.of(AAPL), NewsStatus.RECEIVED, Optional.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_item_concerns_at_least_one_ticker() {
        assertThatThrownBy(() -> new NewsItem(UUID.randomUUID(), "h", "", "https://example.com/a", Instant.now(),
                "Reuters", Set.of(), NewsStatus.RECEIVED, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
