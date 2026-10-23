package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A stored news article. One item can concern several tickers (the same URL fetched for AAPL and MSFT).
 * {@code duplicateOf} points to the original of a near-duplicate headline; such an item is always {@code SKIPPED}
 * (ADR 0009).
 */
public record NewsItem(UUID id, String headline, String summary, String url, Instant publishedAt, String source,
                       Set<Ticker> tickers, NewsStatus status, Optional<UUID> duplicateOf) {

    public NewsItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(headline, "headline");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(source, "source");
        tickers = Set.copyOf(Objects.requireNonNull(tickers, "tickers"));
        if (tickers.isEmpty()) {
            throw new IllegalArgumentException("A news item concerns at least one ticker");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(duplicateOf, "duplicateOf");
        if (duplicateOf.isPresent() && status != NewsStatus.SKIPPED) {
            throw new IllegalArgumentException("A near-duplicate must be SKIPPED, was " + status);
        }
    }

    /** A newly fetched item: {@code SKIPPED} if it duplicates {@code original}, otherwise {@code RECEIVED}. */
    public static NewsItem fetched(UUID id, FetchedNews news, Optional<UUID> original) {
        NewsStatus status = original.isPresent() ? NewsStatus.SKIPPED : NewsStatus.RECEIVED;
        return new NewsItem(id, news.headline(), news.summary(), news.url(), news.publishedAt(), news.source(),
                Set.of(news.ticker()), status, original);
    }
}
