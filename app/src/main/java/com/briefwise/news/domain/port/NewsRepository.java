package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: stored news items. */
public interface NewsRepository {

    Optional<UUID> findIdByUrlHash(UrlHash urlHash);

    /** Adds the ticker to an existing item; no-op if it is already there. */
    void addTicker(UUID newsId, Ticker ticker);

    /**
     * The most similar original item (not itself a near-duplicate) for the same ticker, published within the
     * policy window around {@code publishedAt}, whose headline similarity reaches the policy threshold.
     */
    Optional<UUID> findNearDuplicate(Ticker ticker, String headline, Instant publishedAt, DedupPolicy policy);

    void insert(NewsItem item, UrlHash urlHash);

    Optional<NewsItem> findById(UUID newsId);

    /**
     * Sets {@code SKIPPED} only if the item is still {@code RECEIVED} (the first writer wins, K3 in ADR 0011).
     *
     * @return false if the item was no longer {@code RECEIVED} (or does not exist)
     */
    boolean skipFromReceived(UUID newsId);

    /**
     * Stores the labels, marks the affected tickers and sets {@code CLASSIFIED}, only if the item is still
     * {@code RECEIVED} (first writer wins).
     *
     * @return false if the item was no longer {@code RECEIVED}
     */
    boolean classifyFromReceived(UUID newsId, NewsClassification classification, Instant classifiedAt);

    /**
     * Sets {@code UNCLASSIFIED} with the reason, only if the item is still {@code RECEIVED}.
     *
     * @return false if the item was no longer {@code RECEIVED}
     */
    boolean markUnclassifiedFromReceived(UUID newsId, UnclassifiedReason reason, Instant classifiedAt);
}
