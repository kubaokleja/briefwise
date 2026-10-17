package com.briefwise.portfolio.infrastructure.web;

import com.briefwise.portfolio.PortfolioFacade;
import com.briefwise.portfolio.PortfolioFacade.PortfolioView;
import com.briefwise.portfolio.PortfolioFacade.PositionView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: calls the facade only, no business logic. */
@RestController
@RequestMapping("/api/portfolios")
class PortfolioController {

    private final PortfolioFacade facade;

    PortfolioController(PortfolioFacade facade) {
        this.facade = facade;
    }

    @GetMapping
    List<PortfolioResponse> list() {
        return facade.listPortfolios().stream().map(PortfolioResponse::from).toList();
    }

    @GetMapping("/{portfolioId}")
    PortfolioResponse get(@PathVariable UUID portfolioId) {
        return PortfolioResponse.from(facade.getPortfolio(portfolioId));
    }

    @GetMapping("/{portfolioId}/holdings")
    List<PositionResponse> holdings(@PathVariable UUID portfolioId) {
        return facade.getHoldings(portfolioId).stream().map(PositionResponse::from).toList();
    }

    record PositionResponse(String ticker, BigDecimal quantity, BigDecimal weight) {
        static PositionResponse from(PositionView view) {
            return new PositionResponse(view.ticker().symbol(), view.quantity(), view.weight());
        }
    }

    record PortfolioResponse(UUID id, UUID clientId, String name, String riskProfile,
                             List<PositionResponse> positions) {
        static PortfolioResponse from(PortfolioView view) {
            return new PortfolioResponse(view.id(), view.clientId(), view.name(), view.riskProfile().name(),
                    view.positions().stream().map(PositionResponse::from).toList());
        }
    }
}
