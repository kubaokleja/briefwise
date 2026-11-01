package com.briefwise.portfolio.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A portfolio's latest daily move (M1 slice 10). Each position contributes its weight times its own daily change;
 * the portfolio change is the sum of the contributions. Positions without a price are listed in {@code unpriced} and
 * add nothing; {@code pricedWeight} says how much of the portfolio the number covers. Weights are the stored
 * portfolio weights, not re-derived from market values. {@code asOf} is the latest close date among the positions
 * (instruments may close on different days, e.g. holidays); each position keeps its own date.
 */
public record PortfolioDailyMove(UUID portfolioId, List<PositionMove> positions, List<Ticker> unpriced,
                                 BigDecimal pricedWeight, Optional<BigDecimal> changePercent,
                                 Optional<LocalDate> asOf) {

    /** Scale of percent values, as in {@code market}. */
    public static final int SCALE = 4;

    /** {@code contribution}: weight x change, in percent of the whole portfolio. */
    public record PositionMove(Ticker ticker, BigDecimal weight, LocalDate date, BigDecimal close,
                               BigDecimal changePercent, BigDecimal contribution) {

        public PositionMove {
            Objects.requireNonNull(ticker, "ticker");
            Objects.requireNonNull(weight, "weight");
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(close, "close");
            Objects.requireNonNull(changePercent, "changePercent");
            Objects.requireNonNull(contribution, "contribution");
        }
    }

    public PortfolioDailyMove {
        Objects.requireNonNull(portfolioId, "portfolioId");
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        unpriced = List.copyOf(Objects.requireNonNull(unpriced, "unpriced"));
        Objects.requireNonNull(pricedWeight, "pricedWeight");
        Objects.requireNonNull(changePercent, "changePercent");
        Objects.requireNonNull(asOf, "asOf");
    }

    /** Positions keep the portfolio's order; {@code moves} may lack tickers that have no price. */
    public static PortfolioDailyMove of(Portfolio portfolio, Map<Ticker, InstrumentMove> moves) {
        List<PositionMove> priced = new ArrayList<>();
        List<Ticker> unpriced = new ArrayList<>();
        for (Position p : portfolio.positions()) {
            InstrumentMove m = moves.get(p.ticker());
            if (m == null) {
                unpriced.add(p.ticker());
            } else {
                priced.add(new PositionMove(p.ticker(), p.weight(), m.date(), m.close(), m.changePercent(),
                        p.weight().multiply(m.changePercent()).setScale(SCALE, RoundingMode.HALF_UP)));
            }
        }
        BigDecimal pricedWeight = priced.stream().map(PositionMove::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<BigDecimal> change = priced.isEmpty()
                ? Optional.empty()
                : Optional.of(priced.stream().map(PositionMove::contribution).reduce(BigDecimal.ZERO, BigDecimal::add));
        Optional<LocalDate> asOf = priced.stream().map(PositionMove::date).max(Comparator.naturalOrder());
        return new PortfolioDailyMove(portfolio.id(), priced, unpriced, pricedWeight, change, asOf);
    }
}
