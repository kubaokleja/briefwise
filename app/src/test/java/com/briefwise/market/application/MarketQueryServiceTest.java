package com.briefwise.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.briefwise.market.domain.model.DailyMove;
import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.model.Instrument;
import com.briefwise.market.domain.model.InstrumentNotFoundException;
import com.briefwise.market.domain.model.InstrumentType;
import com.briefwise.market.domain.model.PriceDataUnavailableException;
import com.briefwise.market.domain.port.InstrumentRepository;
import com.briefwise.market.domain.port.PriceRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketQueryServiceTest {

    private final InstrumentRepository instruments = mock(InstrumentRepository.class);
    private final PriceRepository prices = mock(PriceRepository.class);
    private final MarketQueryService service = new MarketQueryService(instruments, prices);

    private final Ticker ticker = new Ticker("AAPL");
    private final Instrument instrument =
            new Instrument(UUID.randomUUID(), ticker, "Apple Inc.", InstrumentType.STOCK, "USD");
    private final LocalDate d1 = LocalDate.parse("2026-10-14");
    private final LocalDate d2 = LocalDate.parse("2026-10-15");

    private static DailyPrice price(LocalDate date, String close) {
        return new DailyPrice(date, new BigDecimal(close), 10);
    }

    @Test
    void price_history_delegates_with_the_instrument_id() {
        List<DailyPrice> history = List.of(price(d1, "100"), price(d2, "101"));
        when(instruments.findByTicker(ticker)).thenReturn(Optional.of(instrument));
        when(prices.findBetween(instrument.id(), d1, d2)).thenReturn(history);

        assertThat(service.getPriceHistory(ticker, d1, d2)).isEqualTo(history);
    }

    @Test
    void price_history_rejects_inverted_range_before_touching_storage() {
        assertThatThrownBy(() -> service.getPriceHistory(ticker, d2, d1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(instruments, prices);
    }

    @Test
    void unknown_ticker_is_not_found() {
        when(instruments.findByTicker(ticker)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPriceHistory(ticker, d1, d2))
                .isInstanceOf(InstrumentNotFoundException.class);
        assertThatThrownBy(() -> service.getDailyMove(ticker)).isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    void daily_move_uses_the_two_latest_closes() {
        when(instruments.findByTicker(ticker)).thenReturn(Optional.of(instrument));
        when(prices.findLatest(instrument.id(), 2)).thenReturn(List.of(price(d2, "110"), price(d1, "100")));

        DailyMove move = service.getDailyMove(ticker);

        assertThat(move.date()).isEqualTo(d2);
        assertThat(move.changePercent()).isEqualByComparingTo("10");
    }

    @Test
    void daily_move_needs_two_closes() {
        when(instruments.findByTicker(ticker)).thenReturn(Optional.of(instrument));
        when(prices.findLatest(instrument.id(), 2)).thenReturn(List.of(price(d2, "110")));

        assertThatThrownBy(() -> service.getDailyMove(ticker)).isInstanceOf(PriceDataUnavailableException.class);
    }
}
