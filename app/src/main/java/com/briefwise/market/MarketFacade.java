package com.briefwise.market;

import com.briefwise.market.domain.model.DailyMove;
import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.model.ImportSummary;
import com.briefwise.market.domain.model.Instrument;
import com.briefwise.market.domain.port.MarketQueries;
import com.briefwise.market.domain.port.PriceSnapshotImport;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code market}. Return types are the nested view records, so the
 * domain model stays private to the context. No logic here: delegate and map.
 */
@Component
public class MarketFacade {

    private final MarketQueries queries;
    private final PriceSnapshotImport snapshotImport;

    MarketFacade(MarketQueries queries, PriceSnapshotImport snapshotImport) {
        this.queries = queries;
        this.snapshotImport = snapshotImport;
    }

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown */
    public InstrumentView getInstrument(Ticker ticker) {
        Instrument instrument = queries.getInstrument(ticker);
        return new InstrumentView(instrument.ticker(), instrument.name(),
                InstrumentTypeView.valueOf(instrument.type().name()), instrument.currency());
    }

    /**
     * Closes from {@code from} to {@code to} inclusive, oldest first.
     *
     * @throws IllegalArgumentException if {@code from} is after {@code to}
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown
     */
    public List<PricePointView> getPriceHistory(Ticker ticker, LocalDate from, LocalDate to) {
        return queries.getPriceHistory(ticker, from, to).stream().map(MarketFacade::toView).toList();
    }

    /**
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown or has fewer
     *         than two stored closes
     */
    public DailyMoveView getDailyMove(Ticker ticker) {
        return toView(queries.getDailyMove(ticker));
    }

    /**
     * Loads the committed price snapshot (ADR 0004). Safe to repeat: closes already stored are not touched.
     *
     * @throws IllegalStateException if the snapshot directory is missing or a file is malformed
     */
    public ImportSummaryView importPriceSnapshot() {
        ImportSummary summary = snapshotImport.importSnapshot();
        return new ImportSummaryView(summary.tickersRead(), summary.rowsRead(), summary.rowsInserted(),
                summary.unknownTickers());
    }

    private static PricePointView toView(DailyPrice price) {
        return new PricePointView(price.date(), price.close(), price.volume());
    }

    private static DailyMoveView toView(DailyMove move) {
        return new DailyMoveView(move.ticker(), move.date(), move.close(), move.previousClose(), move.change(),
                move.changePercent());
    }

    public enum InstrumentTypeView { STOCK, ETF }

    public record InstrumentView(Ticker ticker, String name, InstrumentTypeView type, String currency) {}

    public record ImportSummaryView(int tickersRead, int rowsRead, int rowsInserted, List<Ticker> unknownTickers) {}

    public record PricePointView(LocalDate date, BigDecimal close, long volume) {}

    /** {@code changePercent} is in percent (1.25 means +1.25%). */
    public record DailyMoveView(Ticker ticker, LocalDate date, BigDecimal close, BigDecimal previousClose,
                                BigDecimal change, BigDecimal changePercent) {}
}
