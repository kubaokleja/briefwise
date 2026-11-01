package com.briefwise.portfolio.infrastructure.web;

import com.briefwise.portfolio.PortfolioFacade;
import com.briefwise.portfolio.PortfolioFacade.DailyMoveView;
import com.briefwise.portfolio.PortfolioFacade.PortfolioView;
import com.briefwise.portfolio.PortfolioFacade.PositionMoveView;
import com.briefwise.portfolio.PortfolioFacade.PositionView;
import com.briefwise.shared.domain.model.Ticker;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    @GetMapping("/{portfolioId}/daily-move")
    PortfolioDailyMoveResponse dailyMove(@PathVariable UUID portfolioId) {
        return PortfolioDailyMoveResponse.from(facade.getDailyMove(portfolioId));
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

    record PositionMoveResponse(String ticker, BigDecimal weight, LocalDate date, BigDecimal close,
                                BigDecimal changePercent, BigDecimal contribution) {
        static PositionMoveResponse from(PositionMoveView v) {
            return new PositionMoveResponse(v.ticker().symbol(), v.weight(), v.date(), v.close(), v.changePercent(),
                    v.contribution());
        }
    }

    /**
     * Percent values in percent (1.25 means +1.25%). {@code changePercent} and {@code asOf} are null when no position
     * has a price; {@code pricedWeight} is the share of the portfolio the change covers.
     */
    record PortfolioDailyMoveResponse(
            UUID portfolioId,
            @Schema(types = {"string", "null"}, format = "date") LocalDate asOf,
            @Schema(types = {"number", "null"}) BigDecimal changePercent,
            BigDecimal pricedWeight, List<PositionMoveResponse> positions, List<String> unpriced) {

        static PortfolioDailyMoveResponse from(DailyMoveView v) {
            return new PortfolioDailyMoveResponse(v.portfolioId(), v.asOf().orElse(null),
                    v.changePercent().orElse(null), v.pricedWeight(),
                    v.positions().stream().map(PositionMoveResponse::from).toList(),
                    v.unpriced().stream().map(Ticker::symbol).toList());
        }
    }
}
