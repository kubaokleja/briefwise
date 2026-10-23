package com.briefwise.news.infrastructure.holdings;

import com.briefwise.market.MarketFacade;
import com.briefwise.market.MarketFacade.InstrumentTypeView;
import com.briefwise.news.domain.model.HeldInstrument;
import com.briefwise.news.domain.port.HeldInstruments;
import com.briefwise.portfolio.PortfolioFacade;
import com.briefwise.portfolio.PortfolioFacade.PositionView;
import com.briefwise.shared.domain.model.Ticker;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Held tickers from {@code portfolio}, their kind from {@code market}; only through the two facades. */
@Component
class FacadeHeldInstruments implements HeldInstruments {

    private final PortfolioFacade portfolios;
    private final MarketFacade market;

    FacadeHeldInstruments(PortfolioFacade portfolios, MarketFacade market) {
        this.portfolios = portfolios;
        this.market = market;
    }

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if a held ticker has no instrument */
    @Override
    public List<HeldInstrument> list() {
        return portfolios.listPortfolios().stream()
                .flatMap(portfolio -> portfolio.positions().stream())
                .map(PositionView::ticker)
                .distinct()
                .sorted(Comparator.comparing(Ticker::symbol))
                .map(ticker -> new HeldInstrument(ticker, kindOf(ticker)))
                .toList();
    }

    private HeldInstrument.Kind kindOf(Ticker ticker) {
        return market.getInstrument(ticker).type() == InstrumentTypeView.ETF
                ? HeldInstrument.Kind.FUND
                : HeldInstrument.Kind.COMPANY;
    }
}
