package com.briefwise.portfolio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioDailyMoveTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker AGG = new Ticker("AGG");
    private static final LocalDate MON = LocalDate.parse("2026-10-26");
    private static final LocalDate TUE = LocalDate.parse("2026-10-27");

    private final Portfolio portfolio = new Portfolio(UUID.randomUUID(), new ClientId(UUID.randomUUID()), "Test",
            RiskProfile.BALANCED, List.of(
                    new Position(AAPL, BigDecimal.TEN, new BigDecimal("0.50")),
                    new Position(MSFT, BigDecimal.TEN, new BigDecimal("0.30")),
                    new Position(AGG, BigDecimal.TEN, new BigDecimal("0.20"))));

    private static InstrumentMove move(Ticker ticker, LocalDate date, String changePercent) {
        return new InstrumentMove(ticker, date, new BigDecimal("100"), new BigDecimal(changePercent));
    }

    @Test
    void portfolio_change_is_the_weighted_sum_of_position_changes() {
        PortfolioDailyMove m = PortfolioDailyMove.of(portfolio, Map.of(
                AAPL, move(AAPL, TUE, "2.0000"), MSFT, move(MSFT, TUE, "-1.0000"), AGG, move(AGG, TUE, "0.1234")));

        // 0.5 x 2 + 0.3 x -1 + 0.2 x 0.1234 = 1 - 0.3 + 0.02468 -> 0.0247 rounded per position
        assertThat(m.positions()).extracting(PortfolioDailyMove.PositionMove::contribution)
                .containsExactly(new BigDecimal("1.0000"), new BigDecimal("-0.3000"), new BigDecimal("0.0247"));
        assertThat(m.changePercent()).contains(new BigDecimal("0.7247"));
        assertThat(m.pricedWeight()).isEqualByComparingTo("1");
        assertThat(m.unpriced()).isEmpty();
        assertThat(m.asOf()).contains(TUE);
    }

    @Test
    void unpriced_positions_are_listed_and_the_covered_weight_shrinks() {
        PortfolioDailyMove m = PortfolioDailyMove.of(portfolio, Map.of(
                AAPL, move(AAPL, TUE, "2.0000"), AGG, move(AGG, MON, "-0.5000")));

        assertThat(m.unpriced()).containsExactly(MSFT);
        assertThat(m.pricedWeight()).isEqualByComparingTo("0.70");
        assertThat(m.changePercent()).contains(new BigDecimal("0.9000"));
        assertThat(m.asOf()).contains(TUE);
        assertThat(m.positions()).extracting(PortfolioDailyMove.PositionMove::date).containsExactly(TUE, MON);
    }

    @Test
    void no_prices_means_no_change_rather_than_zero() {
        PortfolioDailyMove m = PortfolioDailyMove.of(portfolio, Map.of());

        assertThat(m.changePercent()).isEmpty();
        assertThat(m.asOf()).isEmpty();
        assertThat(m.pricedWeight()).isEqualByComparingTo("0");
        assertThat(m.unpriced()).containsExactly(AAPL, MSFT, AGG);
    }
}
