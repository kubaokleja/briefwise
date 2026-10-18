package com.briefwise.market.application;

import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.model.ImportSummary;
import com.briefwise.market.domain.model.Instrument;
import com.briefwise.market.domain.port.InstrumentRepository;
import com.briefwise.market.domain.port.PriceRepository;
import com.briefwise.market.domain.port.PriceSnapshotImport;
import com.briefwise.market.domain.port.PriceSnapshotSource;
import com.briefwise.shared.domain.model.Ticker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class PriceSnapshotImportService implements PriceSnapshotImport {

    private final PriceSnapshotSource source;
    private final InstrumentRepository instruments;
    private final PriceRepository prices;

    PriceSnapshotImportService(PriceSnapshotSource source, InstrumentRepository instruments, PriceRepository prices) {
        this.source = source;
        this.instruments = instruments;
        this.prices = prices;
    }

    @Override
    public ImportSummary importSnapshot() {
        int rowsRead = 0;
        int rowsInserted = 0;
        List<Ticker> unknown = new ArrayList<>();
        Map<Ticker, List<DailyPrice>> snapshot = source.read();
        for (Map.Entry<Ticker, List<DailyPrice>> entry : snapshot.entrySet()) {
            rowsRead += entry.getValue().size();
            Optional<Instrument> instrument = instruments.findByTicker(entry.getKey());
            if (instrument.isEmpty()) {
                unknown.add(entry.getKey());
                continue;
            }
            rowsInserted += prices.insertMissing(instrument.get().id(), entry.getValue());
        }
        return new ImportSummary(snapshot.size(), rowsRead, rowsInserted, unknown);
    }
}
