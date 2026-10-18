package com.briefwise.market.infrastructure.snapshot;

import com.briefwise.market.MarketFacade;
import com.briefwise.market.MarketFacade.ImportSummaryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads the committed snapshot when {@code briefwise.market.price-snapshot.import-on-startup=true} (local, demo).
 * A missing or broken snapshot is logged, never fatal: the app is usable without prices.
 */
@Component
@ConditionalOnProperty(prefix = "briefwise.market.price-snapshot", name = "import-on-startup", havingValue = "true")
class PriceSnapshotStartupImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PriceSnapshotStartupImporter.class);

    private final MarketFacade market;

    PriceSnapshotStartupImporter(MarketFacade market) {
        this.market = market;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ImportSummaryView summary = market.importPriceSnapshot();
            log.info("Price snapshot imported: {} files, {} rows read, {} rows inserted, unknown tickers {}",
                    summary.tickersRead(), summary.rowsRead(), summary.rowsInserted(), summary.unknownTickers());
        } catch (RuntimeException e) {
            log.warn("Price snapshot not imported: {}", e.getMessage());
        }
    }
}
