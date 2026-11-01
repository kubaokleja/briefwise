package com.briefwise.portfolio.infrastructure.market;

import com.briefwise.market.MarketFacade;
import com.briefwise.market.MarketFacade.DailyMoveView;
import com.briefwise.portfolio.domain.model.InstrumentMove;
import com.briefwise.portfolio.domain.port.InstrumentMoves;
import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import com.briefwise.shared.domain.model.Ticker;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Daily moves from {@code market}, only through its facade. */
@Component
class FacadeInstrumentMoves implements InstrumentMoves {

    private final MarketFacade market;

    FacadeInstrumentMoves(MarketFacade market) {
        this.market = market;
    }

    @Override
    public Optional<InstrumentMove> latest(Ticker ticker) {
        try {
            DailyMoveView m = market.getDailyMove(ticker);
            return Optional.of(new InstrumentMove(m.ticker(), m.date(), m.close(), m.changePercent()));
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }
}
