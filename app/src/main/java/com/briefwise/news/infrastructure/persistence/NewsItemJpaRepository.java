package com.briefwise.news.infrastructure.persistence;

import java.time.Instant;
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
