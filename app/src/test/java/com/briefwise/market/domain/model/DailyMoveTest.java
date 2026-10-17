package com.briefwise.market.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyMoveTest {

    private static final Ticker AAPL = new Ticker("AAPL");

    private static DailyPrice price(String date, String close) {
        return new DailyPrice(LocalDate.parse(date), new BigDecimal(close), 1_000);
    }

    @Test
    void computes_change_and_percent_for_a_rise() {
        DailyMove move = DailyMove.between(AAPL, price("2026-10-14", "100"), price("2026-10-15", "102.5"));

        assertThat(move.date()).isEqualTo(LocalDate.parse("2026-10-15"));
        assertThat(move.change()).isEqualByComparingTo("2.5");
        assertThat(move.changePercent()).isEqualByComparingTo("2.5");
    }

    @Test
    void computes_a_negative_move_rounded_to_four_decimals() {
        DailyMove move = DailyMove.between(AAPL, price("2026-10-14", "3"), price("2026-10-15", "2"));

        assertThat(move.change()).isEqualByComparingTo("-1");
        assertThat(move.changePercent()).isEqualByComparingTo("-33.3333");
    }

    @Test
    void rejects_prices_not_in_chronological_order() {
        assertThatThrownBy(() -> DailyMove.between(AAPL, price("2026-10-15", "100"), price("2026-10-14", "101")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void daily_price_must_be_positive() {
        assertThatThrownBy(() -> price("2026-10-15", "0")).isInstanceOf(IllegalArgumentException.class);
    }
}
