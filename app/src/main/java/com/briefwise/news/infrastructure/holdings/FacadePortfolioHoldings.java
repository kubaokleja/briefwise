package com.briefwise.news.infrastructure.holdings;

import com.briefwise.news.domain.port.PortfolioHoldings;
import com.briefwise.portfolio.PortfolioFacade;
import com.briefwise.portfolio.PortfolioFacade.PortfolioView;
import com.briefwise.portfolio.PortfolioFacade.PositionView;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Portfolios and weights from {@code portfolio}, only through its facade. */
@Component
class FacadePortfolioHoldings implements PortfolioHoldings {

    private final PortfolioFacade portfolios;

    FacadePortfolioHoldings(PortfolioFacade portfolios) {
        this.portfolios = portfolios;
    }

    @Override
    public List<UUID> portfolioIds() {
        return portfolios.listPortfolios().stream().map(PortfolioView::id).sorted(Comparator.naturalOrder()).toList();
    }

    /** A ticker held twice in one portfolio counts with its summed weight. */
    @Override
    public Map<Ticker, BigDecimal> weights(UUID portfolioId) {
        return portfolios.getHoldings(portfolioId).stream()
                .collect(Collectors.toUnmodifiableMap(PositionView::ticker, PositionView::weight, BigDecimal::add));
    }
}
