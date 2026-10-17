package com.briefwise.portfolio.domain.port;

import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.model.Position;
import java.util.List;
import java.util.UUID;

/** Inbound port: the portfolio use cases. Implemented by a package-private application service. */
public interface PortfolioQueries {

    /** @throws com.briefwise.portfolio.domain.model.PortfolioNotFoundException if the portfolio does not exist */
    Portfolio getPortfolio(UUID portfolioId);

    List<Portfolio> listPortfolios();

    /** @throws com.briefwise.portfolio.domain.model.PortfolioNotFoundException if the portfolio does not exist */
    List<Position> getHoldings(UUID portfolioId);
}
