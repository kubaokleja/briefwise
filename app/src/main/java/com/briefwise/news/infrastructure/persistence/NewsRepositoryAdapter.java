package com.briefwise.news.infrastructure.persistence;

import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Outbound adapter for the news repository port. */
@Component
@Transactional(readOnly = true)
class NewsRepositoryAdapter implements NewsRepository {

    private final NewsItemJpaRepository items;
    private final NewsEntityMapper mapper;

    NewsRepositoryAdapter(NewsItemJpaRepository items, NewsEntityMapper mapper) {
        this.items = items;
        this.mapper = mapper;
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
}
