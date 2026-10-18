package com.briefwise.market.infrastructure.persistence;

import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.model.Instrument;
import com.briefwise.market.domain.port.InstrumentRepository;
import com.briefwise.market.domain.port.PriceRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Outbound adapter for both market ports. */
@Component
@Transactional(readOnly = true)
class MarketRepositoryAdapter implements InstrumentRepository, PriceRepository {

    private final InstrumentJpaRepository instruments;
    private final DailyPriceJpaRepository prices;
    private final MarketEntityMapper mapper;
    private final JdbcTemplate jdbc;

    MarketRepositoryAdapter(InstrumentJpaRepository instruments, DailyPriceJpaRepository prices,
                            MarketEntityMapper mapper, JdbcTemplate jdbc) {
        this.instruments = instruments;
        this.prices = prices;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Instrument> findByTicker(Ticker ticker) {
        return instruments.findByTicker(ticker.symbol()).map(mapper::toDomain);
    }

    @Override
    public List<DailyPrice> findBetween(UUID instrumentId, LocalDate from, LocalDate to) {
        return prices.findByInstrumentIdAndTradeDateBetweenOrderByTradeDateAsc(instrumentId, from, to).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<DailyPrice> findLatest(UUID instrumentId, int count) {
        return prices.findByInstrumentIdOrderByTradeDateDesc(instrumentId, PageRequest.of(0, count)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /** Plain SQL batch: tens of thousands of rows, and {@code ON CONFLICT DO NOTHING} makes re-runs idempotent. */
    @Override
    @Transactional
    public int insertMissing(UUID instrumentId, List<DailyPrice> newPrices) {
        int[][] counts = jdbc.batchUpdate("""
                INSERT INTO daily_prices (instrument_id, trade_date, close, volume) VALUES (?, ?, ?, ?)
                ON CONFLICT (instrument_id, trade_date) DO NOTHING
                """, newPrices, 1000, (statement, price) -> {
            statement.setObject(1, instrumentId);
            statement.setObject(2, price.date());
            statement.setBigDecimal(3, price.close());
            statement.setLong(4, price.volume());
        });
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).sum();
    }
}
