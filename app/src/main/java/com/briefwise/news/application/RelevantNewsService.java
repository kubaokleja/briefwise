package com.briefwise.news.application;

import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.SelectionPolicy;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.PortfolioHoldings;
import com.briefwise.news.domain.port.RelevantNewsQuery;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Selects the classified news that matters to one portfolio (ADR 0017). The query is bounded by the window and a
 * safety cap; ordering by relevance and portfolio weight happens in {@link SelectionPolicy}, in code (ADR 0014).
 */
@Service
class RelevantNewsService implements RelevantNewsQuery {

    /** Candidates read per portfolio before the policy cuts to its cap; far above a day's classified news. */
    static final int CANDIDATE_LIMIT = 500;

    private final NewsRepository repository;
    private final PortfolioHoldings holdings;
    private final SelectionPolicy policy;
    private final Clock clock;

    RelevantNewsService(NewsRepository repository, PortfolioHoldings holdings, SelectionPolicy policy, Clock clock) {
        this.repository = repository;
        this.holdings = holdings;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public List<RelevantNews> relevantFor(UUID portfolioId) {
        Map<Ticker, BigDecimal> weights = holdings.weights(portfolioId);
        if (weights.isEmpty()) {
            return List.of();
        }
        return policy.select(repository.findClassified(weights.keySet(), clock.instant().minus(policy.window()),
                policy.relevances(), CANDIDATE_LIMIT), weights);
    }
}
