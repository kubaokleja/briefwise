package com.briefwise.portfolio.application;

import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.model.PortfolioNotFoundException;
import com.briefwise.portfolio.domain.model.Position;
import com.briefwise.portfolio.domain.port.PortfolioQueries;
import com.briefwise.portfolio.domain.port.PortfolioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class PortfolioQueryService implements PortfolioQueries {

    private final PortfolioRepository portfolios;

    PortfolioQueryService(PortfolioRepository portfolios) {
        this.portfolios = portfolios;
    }

    @Override
    public Portfolio getPortfolio(UUID portfolioId) {
        return portfolios.findById(portfolioId).orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }

    @Override
    public List<Portfolio> listPortfolios() {
        return portfolios.findAll();
    }

    @Override
    public List<Position> getHoldings(UUID portfolioId) {
        return getPortfolio(portfolioId).positions();
    }
}
