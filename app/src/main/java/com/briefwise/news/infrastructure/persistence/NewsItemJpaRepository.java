package com.briefwise.news.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NewsItemJpaRepository extends JpaRepository<NewsItemEntity, UUID> {

    @Query("SELECT n.id FROM NewsItemEntity n WHERE n.urlHash = :urlHash")
    Optional<UUID> findIdByUrlHash(@Param("urlHash") String urlHash);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO news_item_tickers (news_item_id, ticker) VALUES (:newsId, :ticker)
            ON CONFLICT (news_item_id, ticker) DO NOTHING
            """)
    int addTicker(@Param("newsId") UUID newsId, @Param("ticker") String ticker);

    /** Conditional on the current status, so of two concurrent writers only the first changes the row (K3). */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE news_items SET status = 'SKIPPED', updated_at = now() WHERE id = :newsId AND status = 'RECEIVED'
            """)
    int skipFromReceived(@Param("newsId") UUID newsId);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE news_items
            SET status = 'CLASSIFIED', sentiment = :sentiment, relevance = :relevance, event_type = :eventType,
                rationale = :rationale, prompt_version = :promptVersion, classified_at = :at, updated_at = now()
            WHERE id = :newsId AND status = 'RECEIVED'
            """)
    int classifyFromReceived(@Param("newsId") UUID newsId, @Param("sentiment") String sentiment,
                             @Param("relevance") String relevance, @Param("eventType") String eventType,
                             @Param("rationale") String rationale, @Param("promptVersion") String promptVersion,
                             @Param("at") Instant at);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE news_item_tickers SET affected = true, updated_at = now()
            WHERE news_item_id = :newsId AND ticker IN (:tickers)
            """)
    int markAffected(@Param("newsId") UUID newsId, @Param("tickers") Collection<String> tickers);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE news_items
            SET status = 'UNCLASSIFIED', unclassified_reason = :reason, classified_at = :at, updated_at = now()
            WHERE id = :newsId AND status = 'RECEIVED'
            """)
    int markUnclassifiedFromReceived(@Param("newsId") UUID newsId, @Param("reason") String reason,
                                     @Param("at") Instant at);

    /**
     * pg_trgm {@code similarity()} is computed per candidate; the candidates are already narrowed to one ticker and
     * the time window, so a trigram index on the headline would not help this query (ADR 0009).
     */
    @Query(nativeQuery = true, value = """
            SELECT n.id FROM news_items n
            JOIN news_item_tickers t ON t.news_item_id = n.id
            WHERE t.ticker = :ticker
              AND n.duplicate_of_id IS NULL
              AND n.published_at BETWEEN :from AND :to
              AND similarity(n.headline, :headline) >= :threshold
            ORDER BY similarity(n.headline, :headline) DESC, n.published_at
            LIMIT 1
            """)
    Optional<UUID> findNearDuplicate(@Param("ticker") String ticker, @Param("headline") String headline,
                                     @Param("from") Instant from, @Param("to") Instant to,
                                     @Param("threshold") double threshold);
}
