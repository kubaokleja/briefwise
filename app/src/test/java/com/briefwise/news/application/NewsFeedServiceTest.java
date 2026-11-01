package com.briefwise.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.port.NewsFeed;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.PortfolioHoldings;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsFeedServiceTest {

    private static final UUID PORTFOLIO = UUID.randomUUID();

    private final NewsRepository repository = mock(NewsRepository.class);
    private final PortfolioHoldings holdings = mock(PortfolioHoldings.class);
    private final NewsFeedService service = new NewsFeedService(repository, holdings);

    @Test
    void without_a_portfolio_reads_all_news() {
        when(repository.findFeed(Optional.empty(), 50)).thenReturn(List.of());

        assertThat(service.latest(Optional.empty(), 50)).isEmpty();

        verify(repository).findFeed(Optional.empty(), 50);
        verifyNoInteractions(holdings);
    }

    @Test
    void with_a_portfolio_reads_news_of_its_held_tickers() {
        when(holdings.weights(PORTFOLIO)).thenReturn(Map.of(new Ticker("AAPL"), new BigDecimal("0.1")));

        service.latest(Optional.of(PORTFOLIO), 20);

        verify(repository).findFeed(Optional.of(Set.of(new Ticker("AAPL"))), 20);
    }

    @Test
    void a_portfolio_without_positions_has_no_news() {
        when(holdings.weights(PORTFOLIO)).thenReturn(Map.of());

        assertThat(service.latest(Optional.of(PORTFOLIO), 50)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void limit_outside_the_allowed_range_is_rejected() {
        assertThatThrownBy(() -> service.latest(Optional.empty(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.latest(Optional.empty(), NewsFeed.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
