package com.briefwise.portfolio.domain.port;

import com.briefwise.portfolio.domain.model.InstrumentMove;
import com.briefwise.shared.domain.model.Ticker;
import java.util.Optional;

/** Outbound port: latest daily move of an instrument (implemented over the market facade). */
public interface InstrumentMoves {

    /** Empty if the ticker is unknown or has fewer than two stored closes. */
    Optional<InstrumentMove> latest(Ticker ticker);
}
