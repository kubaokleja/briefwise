package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.PortfolioSummary;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: stored portfolio summaries, one per portfolio and UTC date. */
public interface SummaryRepository {

    /** The newest summary of the portfolio, of any date. */
    Optional<PortfolioSummary> findLatest(UUID portfolioId);

    /** Inserts, or replaces the summary of the same portfolio and date. */
    void save(PortfolioSummary summary);
}
