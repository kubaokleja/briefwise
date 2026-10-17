package com.briefwise.market.domain.port;

import com.briefwise.market.domain.model.DailyMove;
import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.shared.domain.model.Ticker;
import java.time.LocalDate;
import java.util.List;

/** Inbound port: market data reads. Implemented by a package-private application service. */
public interface MarketQueries {

    /**
     * Closes from {@code from} to {@code to} inclusive, oldest first.
     *
     * @throws IllegalArgumentException if {@code from} is after {@code to}
     * @throws com.briefwise.market.domain.model.InstrumentNotFoundException if the ticker is unknown
     */
    List<DailyPrice> getPriceHistory(Ticker ticker, LocalDate from, LocalDate to);

    /**
     * @throws com.briefwise.market.domain.model.InstrumentNotFoundException if the ticker is unknown
     * @throws com.briefwise.market.domain.model.PriceDataUnavailableException if fewer than two closes are stored
     */
    DailyMove getDailyMove(Ticker ticker);
}
