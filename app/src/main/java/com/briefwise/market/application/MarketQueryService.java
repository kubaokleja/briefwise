package com.briefwise.market.application;

import com.briefwise.market.domain.model.DailyMove;
import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.model.Instrument;
import com.briefwise.market.domain.model.InstrumentNotFoundException;
import com.briefwise.market.domain.model.PriceDataUnavailableException;
import com.briefwise.market.domain.port.InstrumentRepository;
import com.briefwise.market.domain.port.MarketQueries;
import com.briefwise.market.domain.port.PriceRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class MarketQueryService implements MarketQueries {

    private final InstrumentRepository instruments;
    private final PriceRepository prices;

    MarketQueryService(InstrumentRepository instruments, PriceRepository prices) {
        this.instruments = instruments;
        this.prices = prices;
    }

    @Override
    public Instrument getInstrument(Ticker ticker) {
        return requireInstrument(ticker);
    }

    @Override
    public List<DailyPrice> getPriceHistory(Ticker ticker, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' (" + from + ") must not be after 'to' (" + to + ")");
        }
        return prices.findBetween(requireInstrument(ticker).id(), from, to);
    }

    @Override
    public DailyMove getDailyMove(Ticker ticker) {
        List<DailyPrice> latestTwo = prices.findLatest(requireInstrument(ticker).id(), 2);
        if (latestTwo.size() < 2) {
            throw new PriceDataUnavailableException(ticker, "at least two daily closes are needed");
        }
        return DailyMove.between(ticker, latestTwo.get(1), latestTwo.get(0));
    }

    private Instrument requireInstrument(Ticker ticker) {
        return instruments.findByTicker(ticker).orElseThrow(() -> new InstrumentNotFoundException(ticker));
    }
}
