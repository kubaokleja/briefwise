package com.briefwise.research.infrastructure.market;

import com.briefwise.market.MarketFacade;
import com.briefwise.market.MarketFacade.InstrumentTypeView;
import com.briefwise.market.MarketFacade.InstrumentView;
import com.briefwise.portfolio.PortfolioFacade;
import com.briefwise.portfolio.PortfolioFacade.PositionView;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.Company;
import com.briefwise.research.domain.port.Companies;
import com.briefwise.shared.domain.model.Ticker;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Instruments and their CIK from market (ADR 0026), the held set from the portfolios at run time (ADR 0025). */
@Component
class FacadeCompanies implements Companies {

    private final MarketFacade market;
    private final PortfolioFacade portfolios;

    FacadeCompanies(MarketFacade market, PortfolioFacade portfolios) {
        this.market = market;
        this.portfolios = portfolios;
    }

    @Override
    public Company get(Ticker ticker) {
        InstrumentView instrument = market.getInstrument(ticker);
        return new Company(instrument.ticker(),
                instrument.type() == InstrumentTypeView.ETF ? Company.Kind.FUND : Company.Kind.COMPANY,
                instrument.cik().map(Cik::new));
    }

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if a held ticker has no instrument */
    @Override
    public List<Company> held() {
        return portfolios.listPortfolios().stream()
                .flatMap(portfolio -> portfolio.positions().stream())
                .map(PositionView::ticker)
                .distinct()
                .sorted(Comparator.comparing(Ticker::symbol))
                .map(this::get)
                .toList();
    }
}
