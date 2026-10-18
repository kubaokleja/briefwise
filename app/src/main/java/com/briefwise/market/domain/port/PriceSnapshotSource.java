package com.briefwise.market.domain.port;

import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.Map;

/** Outbound port: the price snapshot produced by the quant refresh script. */
public interface PriceSnapshotSource {

    /**
     * Closes per ticker, oldest first.
     *
     * @throws IllegalStateException if the snapshot is missing or a file is malformed
     */
    Map<Ticker, List<DailyPrice>> read();
}
