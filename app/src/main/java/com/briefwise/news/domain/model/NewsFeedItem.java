package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One item of the news feed (M1 slice 9) in any status a reader may see: {@code RECEIVED} (not classified yet),
 * {@code CLASSIFIED} (with labels) or {@code UNCLASSIFIED} (with the reason). {@code SKIPPED} near-duplicates are never
 * in the feed. {@code tickers}: every ticker the item was fetched for.
 */
public record NewsFeedItem(UUID id, String headline, String url, String source, Instant publishedAt,
                           NewsStatus status, Set<Ticker> tickers, Optional<Labels> labels,
                           Optional<UnclassifiedReason> unclassifiedReason) {

    /** The classifier's labels of a {@code CLASSIFIED} item. */
    public record Labels(Sentiment sentiment, Relevance relevance, EventType eventType, String rationale) {

        public Labels {
            Objects.requireNonNull(sentiment, "sentiment");
            Objects.requireNonNull(relevance, "relevance");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    public NewsFeedItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(headline, "headline");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(status, "status");
        tickers = Set.copyOf(Objects.requireNonNull(tickers, "tickers"));
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(unclassifiedReason, "unclassifiedReason");
        if (status == NewsStatus.SKIPPED) {
            throw new IllegalArgumentException("a SKIPPED item is not part of the feed");
        }
        if ((status == NewsStatus.CLASSIFIED) != labels.isPresent()) {
            throw new IllegalArgumentException("labels are present exactly when the item is CLASSIFIED");
        }
        if ((status == NewsStatus.UNCLASSIFIED) != unclassifiedReason.isPresent()) {
            throw new IllegalArgumentException("a reason is present exactly when the item is UNCLASSIFIED");
        }
    }
}
