package com.briefwise.portfolio.domain.port;

import com.briefwise.portfolio.domain.model.Portfolio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: read access to portfolios with their positions. */
public interface PortfolioRepository {

    Optional<Portfolio> findById(UUID portfolioId);

    List<Portfolio> findAll();
}
