package com.briefwise.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.portfolio.PortfolioFacade.PortfolioView;
import com.briefwise.portfolio.PortfolioFacade.PositionView;
import com.briefwise.portfolio.PortfolioFacade.RiskProfileView;
import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PortfolioFacadeIT extends AbstractIntegrationTest {

    private static final UUID CORE_BALANCED = UUID.fromString("b0000000-0000-4000-8000-000000000002");

    @Autowired
    PortfolioFacade facade;

    @Test
    void lists_the_three_seeded_portfolios_one_per_risk_profile() {
        List<PortfolioView> portfolios = facade.listPortfolios();

        assertThat(portfolios).extracting(PortfolioView::riskProfile)
                .containsExactlyInAnyOrder(RiskProfileView.CONSERVATIVE, RiskProfileView.BALANCED,
                        RiskProfileView.GROWTH);
        assertThat(portfolios).allSatisfy(p -> assertThat(p.positions().stream()
                .map(PositionView::weight).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1"));
    }

    @Test
    void gets_a_portfolio_with_positions_ordered_by_weight() {
        PortfolioView portfolio = facade.getPortfolio(CORE_BALANCED);

        assertThat(portfolio.name()).isEqualTo("Core Balanced");
        assertThat(portfolio.riskProfile()).isEqualTo(RiskProfileView.BALANCED);
        assertThat(portfolio.positions()).hasSize(6);
        assertThat(portfolio.positions().getFirst().ticker().symbol()).isEqualTo("SPY");
    }

    @Test
    void holdings_match_the_portfolio_positions() {
        assertThat(facade.getHoldings(CORE_BALANCED)).isEqualTo(facade.getPortfolio(CORE_BALANCED).positions());
    }

    @Test
    void unknown_portfolio_is_not_found() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

        assertThatThrownBy(() -> facade.getPortfolio(unknown)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> facade.getHoldings(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }
}
