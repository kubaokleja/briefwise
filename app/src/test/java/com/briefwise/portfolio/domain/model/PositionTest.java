package com.briefwise.portfolio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionTest {

    private static final Ticker AAPL = new Ticker("AAPL");

    @Test
    void accepts_a_full_weight() {
        Position position = new Position(AAPL, new BigDecimal("10"), BigDecimal.ONE);

        assertThat(position.weight()).isEqualByComparingTo("1");
    }

    @Test
    void rejects_non_positive_quantity() {
        assertThatThrownBy(() -> new Position(AAPL, BigDecimal.ZERO, new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_weight_outside_zero_exclusive_to_one_inclusive() {
        assertThatThrownBy(() -> new Position(AAPL, BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Position(AAPL, BigDecimal.TEN, new BigDecimal("1.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
