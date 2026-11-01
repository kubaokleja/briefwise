package com.briefwise.portfolio.application;

import com.briefwise.portfolio.domain.model.InstrumentMove;
import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.model.PortfolioDailyMove;
import com.briefwise.portfolio.domain.model.PortfolioNotFoundException;
import com.briefwise.portfolio.domain.model.Position;
import com.briefwise.portfolio.domain.port.InstrumentMoves;
import com.briefwise.portfolio.domain.port.PortfolioQueries;
import com.briefwise.portfolio.domain.port.PortfolioRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class PortfolioQueryService implements PortfolioQueries {

    private final PortfolioRepository portfolios;
    private final InstrumentMoves moves;

    PortfolioQueryService(PortfolioRepository portfolios, InstrumentMoves moves) {
        this.portfolios = portfolios;
        this.moves = moves;
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

    @Override
    public PortfolioDailyMove getDailyMove(UUID portfolioId) {
        Portfolio portfolio = getPortfolio(portfolioId);
        Map<Ticker, InstrumentMove> latest = portfolio.positions().stream()
                .map(p -> moves.latest(p.ticker()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(InstrumentMove::ticker, Function.identity()));
        return PortfolioDailyMove.of(portfolio, latest);
    }
}
