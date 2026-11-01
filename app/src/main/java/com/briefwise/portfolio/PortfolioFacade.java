package com.briefwise.portfolio;

import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.model.PortfolioDailyMove;
import com.briefwise.portfolio.domain.model.Position;
import com.briefwise.portfolio.domain.port.PortfolioQueries;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code portfolio}. Return types are the nested view records, so
 * the domain model stays private to the context. No logic here: delegate and map.
 */
@Component
public class PortfolioFacade {

    private final PortfolioQueries queries;

    PortfolioFacade(PortfolioQueries queries) {
        this.queries = queries;
    }

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist */
    public PortfolioView getPortfolio(UUID portfolioId) {
        return toView(queries.getPortfolio(portfolioId));
    }

    public List<PortfolioView> listPortfolios() {
        return queries.listPortfolios().stream().map(PortfolioFacade::toView).toList();
    }

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist */
    public List<PositionView> getHoldings(UUID portfolioId) {
        return queries.getHoldings(portfolioId).stream().map(PortfolioFacade::toView).toList();
    }

    /**
     * The portfolio's latest daily move: weight x daily change per position, summed (M1 slice 10). Positions without
     * a price are listed, not guessed.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist
     */
    public DailyMoveView getDailyMove(UUID portfolioId) {
        PortfolioDailyMove m = queries.getDailyMove(portfolioId);
        return new DailyMoveView(m.portfolioId(), m.asOf(), m.changePercent(), m.pricedWeight(),
                m.positions().stream().map(p -> new PositionMoveView(p.ticker(), p.weight(), p.date(), p.close(),
                        p.changePercent(), p.contribution())).toList(),
                m.unpriced());
    }

    private static PortfolioView toView(Portfolio portfolio) {
        return new PortfolioView(
                portfolio.id(),
                portfolio.clientId().value(),
                portfolio.name(),
                RiskProfileView.valueOf(portfolio.profile().name()),
                portfolio.positions().stream().map(PortfolioFacade::toView).toList());
    }

    private static PositionView toView(Position position) {
        return new PositionView(position.ticker(), position.quantity(), position.weight());
    }

    public enum RiskProfileView { CONSERVATIVE, BALANCED, GROWTH }

    public record PositionView(Ticker ticker, BigDecimal quantity, BigDecimal weight) {}

    public record PortfolioView(UUID id, UUID clientId, String name, RiskProfileView riskProfile,
                                List<PositionView> positions) {}

    /** Percent values in percent (1.25 means +1.25%); {@code contribution} in percent of the portfolio. */
    public record PositionMoveView(Ticker ticker, BigDecimal weight, LocalDate date, BigDecimal close,
                                   BigDecimal changePercent, BigDecimal contribution) {}

    /**
     * {@code changePercent} is empty when no position has a price; {@code pricedWeight} is the share of the portfolio
     * it covers; {@code asOf} the latest close date among the positions.
     */
    public record DailyMoveView(UUID portfolioId, Optional<LocalDate> asOf, Optional<BigDecimal> changePercent,
                                BigDecimal pricedWeight, List<PositionMoveView> positions, List<Ticker> unpriced) {}
}
