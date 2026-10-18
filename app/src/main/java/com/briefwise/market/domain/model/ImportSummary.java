package com.briefwise.market.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.List;

/**
 * Result of a price snapshot import.
 *
 * @param tickersRead    snapshot files found
 * @param rowsRead       closes in those files
 * @param rowsInserted   closes that were not stored yet (the rest were already there)
 * @param unknownTickers snapshot files without a matching instrument; skipped
 */
public record ImportSummary(int tickersRead, int rowsRead, int rowsInserted, List<Ticker> unknownTickers) {

    public ImportSummary {
        unknownTickers = List.copyOf(unknownTickers);
    }
}
