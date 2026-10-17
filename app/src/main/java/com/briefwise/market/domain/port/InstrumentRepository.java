package com.briefwise.market.domain.port;

import com.briefwise.market.domain.model.Instrument;
import com.briefwise.shared.domain.model.Ticker;
import java.util.Optional;

/** Outbound port: instrument reference data. */
public interface InstrumentRepository {

    Optional<Instrument> findByTicker(Ticker ticker);
}
