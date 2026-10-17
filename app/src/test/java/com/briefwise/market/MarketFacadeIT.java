package com.briefwise.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.market.MarketFacade.DailyMoveView;
import com.briefwise.market.MarketFacade.PricePointView;
import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import com.briefwise.shared.domain.model.Ticker;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Prices are inserted per test and rolled back (@Transactional); only the instruments are seeded reference data. */
@Transactional
class MarketFacadeIT extends AbstractIntegrationTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");

    @Autowired
    MarketFacade facade;

    @Autowired
    JdbcTemplate jdbc;

    private void insertClose(String ticker, String date, String close) {
        jdbc.update("""
                INSERT INTO daily_prices (instrument_id, trade_date, close, volume)
                SELECT id, ?::date, ?::numeric, 1000 FROM instruments WHERE ticker = ?
                """, date, close, ticker);
    }

    @Test
    void price_history_is_inclusive_and_oldest_first() {
        insertClose("AAPL", "2026-10-13", "99");
        insertClose("AAPL", "2026-10-15", "102.5");
        insertClose("AAPL", "2026-10-14", "100");
        insertClose("MSFT", "2026-10-14", "400");

        List<PricePointView> history =
                facade.getPriceHistory(AAPL, LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-15"));

        assertThat(history).extracting(PricePointView::date)
                .containsExactly(LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-15"));
        assertThat(history.getFirst().close()).isEqualByComparingTo("100");
    }

    @Test
    void daily_move_compares_the_two_latest_closes() {
        insertClose("AAPL", "2026-10-13", "50");
        insertClose("AAPL", "2026-10-14", "100");
        insertClose("AAPL", "2026-10-15", "102.5");

        DailyMoveView move = facade.getDailyMove(AAPL);

        assertThat(move.date()).isEqualTo(LocalDate.parse("2026-10-15"));
        assertThat(move.previousClose()).isEqualByComparingTo("100");
        assertThat(move.change()).isEqualByComparingTo("2.5");
        assertThat(move.changePercent()).isEqualByComparingTo("2.5");
    }

    @Test
    void daily_move_without_enough_prices_is_not_found() {
        insertClose("AAPL", "2026-10-15", "102.5");

        assertThatThrownBy(() -> facade.getDailyMove(AAPL)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> facade.getDailyMove(MSFT)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unknown_ticker_is_not_found() {
        Ticker unknown = new Ticker("ZZZZ");

        assertThatThrownBy(() -> facade.getDailyMove(unknown)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> facade.getPriceHistory(unknown, LocalDate.parse("2026-10-14"),
                LocalDate.parse("2026-10-15"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inverted_range_is_rejected() {
        assertThatThrownBy(() -> facade.getPriceHistory(AAPL, LocalDate.parse("2026-10-15"),
                LocalDate.parse("2026-10-14"))).isInstanceOf(IllegalArgumentException.class);
    }
}
