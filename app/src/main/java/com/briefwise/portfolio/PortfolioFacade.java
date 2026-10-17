package com.briefwise.portfolio;

import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.model.Position;
import com.briefwise.portfolio.domain.port.PortfolioQueries;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.util.List;
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
}
