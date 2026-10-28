package com.briefwise.news.infrastructure.persistence;

import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Outbound adapter for the news repository port. */
@Component
@Transactional(readOnly = true)
class NewsRepositoryAdapter implements NewsRepository {

    private final NewsItemJpaRepository items;
    private final NewsEntityMapper mapper;
    private final NamedParameterJdbcTemplate jdbc;

    NewsRepositoryAdapter(NewsItemJpaRepository items, NewsEntityMapper mapper, NamedParameterJdbcTemplate jdbc) {
        this.items = items;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findIdByUrlHash(UrlHash urlHash) {
        return items.findIdByUrlHash(urlHash.value());
    }

    @Override
    @Transactional
    public void addTicker(UUID newsId, Ticker ticker) {
        items.addTicker(newsId, ticker.symbol());
    }

    @Override
    public Optional<UUID> findNearDuplicate(Ticker ticker, String headline, Instant publishedAt, DedupPolicy policy) {
        return items.findNearDuplicate(ticker.symbol(), headline, publishedAt.minus(policy.window()),
                publishedAt.plus(policy.window()), policy.headlineSimilarity());
    }

    @Override
    @Transactional
    public void insert(NewsItem item, UrlHash urlHash) {
        items.save(new NewsItemEntity(item.id(), item.headline(), item.summary(), item.url(), urlHash.value(),
                item.source(), item.publishedAt(), item.status().name(), item.duplicateOf().orElse(null),
                item.tickers().stream().map(Ticker::symbol).collect(Collectors.toSet())));
    }

    @Override
    public Optional<NewsItem> findById(UUID newsId) {
        return items.findById(newsId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public boolean skipFromReceived(UUID newsId) {
        return items.skipFromReceived(newsId) == 1;
    }

    @Override
    @Transactional
    public boolean classifyFromReceived(UUID newsId, NewsClassification c, Instant classifiedAt) {
        int updated = items.classifyFromReceived(newsId, c.sentiment().name(), c.relevance().name(),
                c.eventType().name(), c.rationale(), c.promptVersion(), classifiedAt);
        if (updated == 0) {
            return false;
        }
        items.markAffected(newsId, c.tickers().stream().map(Ticker::symbol).toList());
        return true;
    }

    @Override
    @Transactional
    public boolean markUnclassifiedFromReceived(UUID newsId, UnclassifiedReason reason, Instant classifiedAt) {
        return items.markUnclassifiedFromReceived(newsId, reason.name(), classifiedAt) == 1;
    }

    @Override
    public List<UUID> findSweepable(Instant createdBefore, int limit) {
        return items.findSweepable(createdBefore, limit);
    }

    @Override
    @Transactional
    public int reopenProviderErrors(Instant createdBefore, Instant classifiedSince, int maxAttempts) {
        return items.reopenProviderErrors(createdBefore, classifiedSince, maxAttempts);
    }

    @Override
    @Transactional
    public int incrementSweepAttempts(UUID newsId) {
        if (items.incrementSweepAttempts(newsId) == 0) {
            throw new NewsItemNotFoundException(newsId);
        }
        return items.findSweepAttempts(newsId);
    }

    /**
     * Plain JDBC: one row per item with its affected tickers aggregated, which a JPA projection cannot map. Served by
     * {@code news_items_selection_idx} and {@code news_item_tickers_affected_idx} (V15).
     */
    @Override
    public List<ClassifiedNews> findClassified(Set<Ticker> tickers, Instant publishedSince,
                                               Set<Relevance> relevances, int limit) {
        if (tickers.isEmpty() || relevances.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tickers", tickers.stream().map(Ticker::symbol).toList())
                .addValue("relevances", relevances.stream().map(Relevance::name).toList())
                .addValue("since", Timestamp.from(publishedSince))
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT n.id, n.headline, n.summary, n.url, n.source, n.published_at, n.classified_at, n.sentiment,
                       n.relevance, n.event_type, n.rationale, array_agg(t.ticker ORDER BY t.ticker) AS tickers
                FROM news_items n
                JOIN news_item_tickers t ON t.news_item_id = n.id AND t.affected
                WHERE n.status = 'CLASSIFIED' AND n.relevance IN (:relevances) AND n.published_at >= :since
                  AND EXISTS (SELECT 1 FROM news_item_tickers h
                              WHERE h.news_item_id = n.id AND h.affected AND h.ticker IN (:tickers))
                GROUP BY n.id
                ORDER BY n.published_at DESC, n.id
                LIMIT :limit""", params, (rs, row) -> new ClassifiedNews(
                rs.getObject("id", UUID.class), rs.getString("headline"), rs.getString("summary"),
                rs.getString("url"), rs.getString("source"), rs.getTimestamp("published_at").toInstant(),
                rs.getTimestamp("classified_at").toInstant(),
                Arrays.stream((String[]) rs.getArray("tickers").getArray()).map(Ticker::new)
                        .collect(Collectors.toSet()),
                Sentiment.valueOf(rs.getString("sentiment")), Relevance.valueOf(rs.getString("relevance")),
                EventType.valueOf(rs.getString("event_type")), rs.getString("rationale")));
    }
}
