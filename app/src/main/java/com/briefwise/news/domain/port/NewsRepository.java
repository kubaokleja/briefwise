package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Ids of {@code RECEIVED} items created before {@code createdBefore} that have no unpublished outbox row (those
     * belong to the relay), oldest first.
     */
    List<UUID> findSweepable(Instant createdBefore, int limit);

    /**
     * Puts {@code UNCLASSIFIED} {@code PROVIDER_ERROR} items back to {@code RECEIVED} (labels and reason cleared) if
     * they were created before {@code createdBefore}, classified at or after {@code classifiedSince} and have fewer
     * than {@code maxAttempts} sweep attempts.
     *
     * @return how many items were reopened
     */
    int reopenProviderErrors(Instant createdBefore, Instant classifiedSince, int maxAttempts);

    /**
     * Counts one sweep attempt.
     *
     * @return the attempts including this one
     */
    int incrementSweepAttempts(UUID newsId);

    /**
     * {@code CLASSIFIED} items with one of {@code relevances}, published at or after {@code publishedSince}, with at
     * least one of {@code tickers} among their affected tickers; each with all its affected tickers. Unordered and
     * capped at {@code limit} newest; the selection policy orders and cuts.
     */
    List<ClassifiedNews> findClassified(Set<Ticker> tickers, Instant publishedSince, Set<Relevance> relevances,
                                        int limit);
}
