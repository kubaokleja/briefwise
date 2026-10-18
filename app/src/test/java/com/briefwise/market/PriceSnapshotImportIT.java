package com.briefwise.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.market.MarketFacade.DailyMoveView;
import com.briefwise.market.MarketFacade.ImportSummaryView;
import com.briefwise.shared.domain.model.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports the real committed snapshot ({@code data/prices}, relative to {@code app/}) and rolls it back, so the
 * other market tests still see an empty price table. Also guards that V4/V6 and the snapshot stay in sync.
 */
@Transactional
class PriceSnapshotImportIT extends AbstractIntegrationTest {

    @Autowired
    MarketFacade facade;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void imports_every_snapshot_file_into_a_seeded_instrument() {
        ImportSummaryView summary = facade.importPriceSnapshot();

        assertThat(summary.unknownTickers()).isEmpty();
        assertThat(summary.tickersRead()).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM instruments", Integer.class));
        assertThat(summary.rowsRead()).isPositive();
        assertThat(summary.rowsInserted()).isEqualTo(summary.rowsRead());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_prices", Integer.class))
                .isEqualTo(summary.rowsInserted());
    }

    @Test
    void importing_twice_inserts_nothing_the_second_time() {
        ImportSummaryView first = facade.importPriceSnapshot();
        ImportSummaryView second = facade.importPriceSnapshot();

        assertThat(second.rowsRead()).isEqualTo(first.rowsRead());
        assertThat(second.rowsInserted()).isZero();
    }

    @Test
    void imported_prices_are_served_by_the_existing_queries() {
        facade.importPriceSnapshot();

        DailyMoveView move = facade.getDailyMove(new Ticker("AAPL"));

        assertThat(move.close()).isPositive();
        assertThat(move.previousClose()).isPositive();
    }
}
