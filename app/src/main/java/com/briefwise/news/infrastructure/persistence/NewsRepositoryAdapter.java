package com.briefwise.news.infrastructure.persistence;

import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
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
    public boolean transitionFromReceived(UUID newsId, NewsStatus to) {
        return items.transitionFromReceived(newsId, to.name()) == 1;
    }
}
