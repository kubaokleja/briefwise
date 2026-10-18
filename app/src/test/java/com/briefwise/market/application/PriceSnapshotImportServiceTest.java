package com.briefwise.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.model.ImportSummary;
import com.briefwise.market.domain.model.Instrument;
import com.briefwise.market.domain.model.InstrumentType;
import com.briefwise.market.domain.port.InstrumentRepository;
import com.briefwise.market.domain.port.PriceRepository;
import com.briefwise.market.domain.port.PriceSnapshotSource;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriceSnapshotImportServiceTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker ZZZZ = new Ticker("ZZZZ");

    private final PriceSnapshotSource source = mock(PriceSnapshotSource.class);
    private final InstrumentRepository instruments = mock(InstrumentRepository.class);
    private final PriceRepository prices = mock(PriceRepository.class);
    private final PriceSnapshotImportService service = new PriceSnapshotImportService(source, instruments, prices);

    private static DailyPrice close(String date, String close) {
        return new DailyPrice(LocalDate.parse(date), new BigDecimal(close), 1000);
    }

    @Test
    void stores_the_closes_of_known_instruments_and_reports_the_counts() {
        UUID id = UUID.randomUUID();
        List<DailyPrice> closes = List.of(close("2026-10-14", "100"), close("2026-10-15", "101"));
        when(source.read()).thenReturn(Map.of(AAPL, closes));
        when(instruments.findByTicker(AAPL))
                .thenReturn(Optional.of(new Instrument(id, AAPL, "Apple Inc.", InstrumentType.STOCK, "USD")));
        when(prices.insertMissing(id, closes)).thenReturn(1);

        ImportSummary summary = service.importSnapshot();

        assertThat(summary).isEqualTo(new ImportSummary(1, 2, 1, List.of()));
    }

    @Test
    void skips_tickers_without_an_instrument_and_reports_them() {
        Map<Ticker, List<DailyPrice>> snapshot = new LinkedHashMap<>();
        snapshot.put(ZZZZ, List.of(close("2026-10-14", "5")));
        when(source.read()).thenReturn(snapshot);
        when(instruments.findByTicker(ZZZZ)).thenReturn(Optional.empty());

        ImportSummary summary = service.importSnapshot();

        assertThat(summary.unknownTickers()).containsExactly(ZZZZ);
        assertThat(summary.rowsRead()).isEqualTo(1);
        assertThat(summary.rowsInserted()).isZero();
        verify(prices, never()).insertMissing(any(), any());
    }
}
