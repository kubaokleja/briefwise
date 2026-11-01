package com.briefwise.news.application;

import com.briefwise.news.domain.model.NewsFeedItem;
import com.briefwise.news.domain.port.NewsFeed;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.PortfolioHoldings;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads the news feed (M1 slice 9); with a portfolio, only items fetched for one of its held tickers. */
@Service
class NewsFeedService implements NewsFeed {

    private final NewsRepository repository;
    private final PortfolioHoldings holdings;

    NewsFeedService(NewsRepository repository, PortfolioHoldings holdings) {
        this.repository = repository;
        this.holdings = holdings;
    }

    @Override
    public List<NewsFeedItem> latest(Optional<UUID> portfolioId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", was " + limit);
        }
        Optional<Set<Ticker>> tickers = portfolioId.map(id -> holdings.weights(id).keySet());
        if (tickers.isPresent() && tickers.get().isEmpty()) {
            return List.of();
        }
        return repository.findFeed(tickers, limit);
    }
}
