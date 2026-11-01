package com.briefwise.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.briefwise.portfolio.domain.model.ClientId;
import com.briefwise.portfolio.domain.model.InstrumentMove;
import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.model.PortfolioNotFoundException;
import com.briefwise.portfolio.domain.model.Position;
import com.briefwise.portfolio.domain.model.RiskProfile;
import com.briefwise.portfolio.domain.port.InstrumentMoves;
import com.briefwise.portfolio.domain.port.PortfolioRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioQueryServiceTest {

    private final PortfolioRepository repository = mock(PortfolioRepository.class);
    private final InstrumentMoves moves = mock(InstrumentMoves.class);
    private final PortfolioQueryService service = new PortfolioQueryService(repository, moves);

    private final UUID id = UUID.randomUUID();
    private final Position position = new Position(new Ticker("AAPL"), BigDecimal.TEN, BigDecimal.ONE);
    private final Portfolio portfolio =
            new Portfolio(id, new ClientId(UUID.randomUUID()), "Test", RiskProfile.BALANCED, List.of(position));

    @Test
    void returns_the_portfolio() {
        when(repository.findById(id)).thenReturn(Optional.of(portfolio));

        assertThat(service.getPortfolio(id)).isEqualTo(portfolio);
    }

    @Test
    void unknown_portfolio_is_not_found() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPortfolio(id)).isInstanceOf(PortfolioNotFoundException.class);
        assertThatThrownBy(() -> service.getHoldings(id)).isInstanceOf(PortfolioNotFoundException.class);
    }

    @Test
    void holdings_are_the_positions_of_the_portfolio() {
        when(repository.findById(id)).thenReturn(Optional.of(portfolio));

        assertThat(service.getHoldings(id)).containsExactly(position);
    }

    @Test
    void lists_all_portfolios() {
        when(repository.findAll()).thenReturn(List.of(portfolio));

        assertThat(service.listPortfolios()).containsExactly(portfolio);
    }

    @Test
    void daily_move_uses_the_latest_move_of_each_position() {
        when(repository.findById(id)).thenReturn(Optional.of(portfolio));
        when(moves.latest(new Ticker("AAPL"))).thenReturn(Optional.of(new InstrumentMove(new Ticker("AAPL"),
                LocalDate.parse("2026-10-27"), new BigDecimal("102.5"), new BigDecimal("2.5000"))));

        assertThat(service.getDailyMove(id).changePercent()).contains(new BigDecimal("2.5000"));
    }

    @Test
    void daily_move_of_an_unknown_portfolio_is_not_found() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDailyMove(id)).isInstanceOf(PortfolioNotFoundException.class);
    }
}
