package com.briefwise.news.application;

import static com.briefwise.news.SummaryFixtures.NOW;
import static com.briefwise.news.SummaryFixtures.classified;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.SelectionPolicy;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.PortfolioHoldings;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelevantNewsServiceTest {

    private static final UUID PORTFOLIO = UUID.randomUUID();

    private final NewsRepository repository = mock(NewsRepository.class);
    private final PortfolioHoldings holdings = mock(PortfolioHoldings.class);
    private final RelevantNewsService service = new RelevantNewsService(repository, holdings,
            new SelectionPolicy(Duration.ofHours(24), 15, Relevance.MEDIUM), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void asks_for_held_tickers_within_the_window_and_qualifying_relevances() {
        when(holdings.weights(PORTFOLIO)).thenReturn(Map.of(new Ticker("AAPL"), new BigDecimal("0.1")));
        when(repository.findClassified(anySet(), any(), anySet(), anyInt()))
                .thenReturn(List.of(classified("Apple", Relevance.HIGH, NOW, "AAPL")));

        assertThat(service.relevantFor(PORTFOLIO)).hasSize(1);

        verify(repository).findClassified(Set.of(new Ticker("AAPL")), NOW.minus(Duration.ofHours(24)),
                Set.of(Relevance.MEDIUM, Relevance.HIGH), RelevantNewsService.CANDIDATE_LIMIT);
    }

    @Test
    void a_portfolio_without_positions_has_no_news() {
        when(holdings.weights(PORTFOLIO)).thenReturn(Map.of());

        assertThat(service.relevantFor(PORTFOLIO)).isEmpty();
        verifyNoInteractions(repository);
    }
}
