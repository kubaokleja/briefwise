package com.briefwise.news.application;

import static com.briefwise.news.SummaryFixtures.NOW;
import static com.briefwise.news.SummaryFixtures.relevant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.model.PortfolioSummary;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryDraft;
import com.briefwise.news.domain.model.SummaryOutcome;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.news.domain.model.SummaryRun;
import com.briefwise.news.domain.model.SummaryViolation;
import com.briefwise.news.domain.port.GenerationAudit;
import com.briefwise.news.domain.port.NewsSummarizer;
import com.briefwise.news.domain.port.PortfolioHoldings;
import com.briefwise.news.domain.port.RelevantNewsQuery;
import com.briefwise.news.domain.port.SummaryCompliance;
import com.briefwise.news.domain.port.SummaryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioSummaryServiceTest {

    private static final UUID PORTFOLIO = UUID.randomUUID();
    private static final String DISCLAIMER = "Fixed disclaimer.";
    private static final List<SummaryViolation> FORECAST = List.of(new SummaryViolation("NO_FORECAST", "will rise"));

    private final RelevantNewsQuery relevantNews = mock(RelevantNewsQuery.class);
    private final PortfolioHoldings holdings = mock(PortfolioHoldings.class);
    private final NewsSummarizer summarizer = mock(NewsSummarizer.class);
    private final SummaryCompliance compliance = mock(SummaryCompliance.class);
    private final SummaryRepository repository = mock(SummaryRepository.class);
    private final GenerationAudit audit = mock(GenerationAudit.class);
    private final PortfolioSummaryService service = new PortfolioSummaryService(relevantNews, holdings, summarizer,
            compliance, repository, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    private final RelevantNews apple = relevant("Apple misses estimates", Relevance.HIGH, NOW.minusSeconds(60),
            "AAPL", "0.1");

    @BeforeEach
    void setUp() {
        when(relevantNews.relevantFor(PORTFOLIO)).thenReturn(List.of(apple));
        when(repository.findLatest(PORTFOLIO)).thenReturn(Optional.empty());
        when(compliance.disclaimer()).thenReturn(DISCLAIMER);
        when(compliance.check(anyList(), anyString())).thenReturn(List.of());
    }

    private SummarizerResult drafted(String text) {
        return drafted(text, Optional.empty());
    }

    private SummarizerResult drafted(String text, Optional<UUID> generationId) {
        return new SummarizerResult.Drafted(new SummaryDraft(
                List.of(new SummaryDraft.Point(text, List.of(apple.news().id()))), "claude-haiku-5-5", "v1"),
                generationId);
    }

    private PortfolioSummary saved() {
        ArgumentCaptor<PortfolioSummary> captor = ArgumentCaptor.forClass(PortfolioSummary.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void compliant_draft_is_stored_with_citations_model_and_disclaimer() {
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL missed estimates."));

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.GENERATED);

        PortfolioSummary summary = saved();
        assertThat(summary.fallback()).isFalse();
        assertThat(summary.summaryDate()).isEqualTo(LocalDate.parse("2026-10-28"));
        assertThat(summary.model()).contains("claude-haiku-5-5");
        assertThat(summary.disclaimer()).isEqualTo(DISCLAIMER);
        assertThat(summary.points()).singleElement()
                .satisfies(p -> assertThat(p.citations()).singleElement()
                        .satisfies(c -> assertThat(c.sourceId()).isEqualTo(apple.news().id().toString())));
    }

    @Test
    void generated_at_is_the_selection_time_so_an_item_classified_during_the_call_counts_as_new() {
        Clock moving = mock(Clock.class);
        when(moving.instant()).thenReturn(NOW, NOW.plusSeconds(30));
        PortfolioSummaryService timed = new PortfolioSummaryService(relevantNews, holdings, summarizer, compliance,
                repository, audit, moving);
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL missed estimates."));

        timed.summarize(PORTFOLIO);

        assertThat(saved().generatedAt()).isEqualTo(NOW);
    }

    @Test
    void a_draft_rejected_twice_ends_in_the_template_and_its_log_entry_is_flagged_as_fallback() {
        UUID firstLog = UUID.randomUUID();
        UUID secondLog = UUID.randomUUID();
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL will rise.", Optional.of(firstLog)),
                drafted("AAPL will surely rise.", Optional.of(secondLog)));
        when(compliance.check(anyList(), anyString())).thenReturn(FORECAST, FORECAST, List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.FALLBACK);

        verify(audit).markFallback(secondLog);
        verify(audit, never()).markFallback(firstLog);
    }

    @Test
    void a_rejected_draft_whose_re_ask_is_deferred_is_flagged_as_fallback() {
        UUID firstLog = UUID.randomUUID();
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL will rise.", Optional.of(firstLog)),
                new SummarizerResult.Deferred("budget"));
        when(compliance.check(anyList(), anyString())).thenReturn(FORECAST, List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.FALLBACK);

        verify(audit).markFallback(firstLog);
    }

    @Test
    void a_re_ask_that_failed_in_the_adapter_flags_its_own_entry_so_nothing_more_is_marked() {
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL will rise.", Optional.of(UUID.randomUUID())),
                new SummarizerResult.Failed("INVALID_OUTPUT"));
        when(compliance.check(anyList(), anyString())).thenReturn(FORECAST, List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.FALLBACK);

        verify(audit, never()).markFallback(any());
    }

    @Test
    void a_compliant_draft_flags_nothing() {
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL missed estimates.", Optional.of(UUID.randomUUID())));

        service.summarize(PORTFOLIO);

        verify(audit, never()).markFallback(any());
    }

    @Test
    void nothing_relevant_stores_an_empty_summary_without_a_call() {
        when(relevantNews.relevantFor(PORTFOLIO)).thenReturn(List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.EMPTY);

        assertThat(saved().empty()).isTrue();
        verify(summarizer, never()).summarize(any());
    }

    @Test
    void up_to_date_summary_is_kept_without_a_call() {
        when(repository.findLatest(PORTFOLIO)).thenReturn(Optional.of(new PortfolioSummary(UUID.randomUUID(),
                PORTFOLIO, LocalDate.parse("2026-10-28"), NOW, List.of(), false, Optional.empty(), Optional.empty(),
                DISCLAIMER, List.of())));
        when(relevantNews.relevantFor(PORTFOLIO)).thenReturn(List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.UNCHANGED);

        verify(summarizer, never()).summarize(any());
        verify(repository, never()).save(any());
    }

    @Test
    void violation_is_sent_back_once_and_a_fixed_draft_is_stored() {
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL will rise."), drafted("AAPL missed estimates."));
        when(compliance.check(anyList(), anyString())).thenReturn(FORECAST, List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.GENERATED);

        ArgumentCaptor<SummaryRequest> requests = ArgumentCaptor.forClass(SummaryRequest.class);
        verify(summarizer, times(2)).summarize(requests.capture());
        assertThat(requests.getAllValues().getFirst().corrections()).isEmpty();
        assertThat(requests.getAllValues().getLast().corrections()).isEqualTo(FORECAST);
        assertThat(saved().points().getFirst().text()).isEqualTo("AAPL missed estimates.");
    }

    @Test
    void violation_after_the_re_ask_stores_the_template() {
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL will rise."));
        when(compliance.check(anyList(), anyString())).thenReturn(FORECAST, FORECAST, List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.FALLBACK);

        PortfolioSummary summary = saved();
        assertThat(summary.fallback()).isTrue();
        assertThat(summary.model()).isEmpty();
        assertThat(summary.points()).extracting(p -> p.text())
                .containsExactly("AAPL: Apple misses estimates (negative, earnings)");
        verify(summarizer, times(2)).summarize(any());
    }

    @Test
    void failed_call_stores_the_template_with_anything_it_still_breaks() {
        when(summarizer.summarize(any())).thenReturn(new SummarizerResult.Failed("PROVIDER_ERROR"));
        List<SummaryViolation> quoted = List.of(new SummaryViolation("NO_RECOMMENDATION", "buy the stock"));
        when(compliance.check(anyList(), anyString())).thenReturn(quoted);

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.FALLBACK);

        assertThat(saved().violations()).isEqualTo(quoted);
        verify(summarizer, times(1)).summarize(any());
    }

    @Test
    void deferral_keeps_the_previous_summary() {
        when(summarizer.summarize(any())).thenReturn(new SummarizerResult.Deferred("budget"));

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.DEFERRED);

        verify(repository, never()).save(any());
    }

    @Test
    void deferral_on_the_re_ask_stores_the_template_not_the_flagged_draft() {
        when(summarizer.summarize(any())).thenReturn(drafted("AAPL will rise."),
                new SummarizerResult.Deferred("budget"));
        when(compliance.check(anyList(), anyString())).thenReturn(FORECAST, List.of());

        assertThat(service.summarize(PORTFOLIO)).isEqualTo(SummaryOutcome.FALLBACK);

        assertThat(saved().fallback()).isTrue();
    }

    @Test
    void run_over_all_portfolios_stops_at_the_first_deferral_and_counts_failures() {
        UUID broken = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(holdings.portfolioIds()).thenReturn(List.of(broken, PORTFOLIO, third));
        when(relevantNews.relevantFor(broken)).thenThrow(new IllegalStateException("boom"));
        when(summarizer.summarize(any())).thenReturn(new SummarizerResult.Deferred("budget"));

        SummaryRun run = service.summarizeAll(NOW.plus(java.time.Duration.ofMinutes(15)));

        assertThat(run).isEqualTo(new SummaryRun(3, 0, 0, 0, 0, 1, 1, true, false));
        verify(relevantNews, never()).relevantFor(third);
    }

    @Test
    void run_over_all_portfolios_stops_before_the_next_portfolio_once_the_deadline_has_passed() {
        when(holdings.portfolioIds()).thenReturn(List.of(PORTFOLIO, UUID.randomUUID()));

        SummaryRun run = service.summarizeAll(NOW);

        assertThat(run).isEqualTo(new SummaryRun(2, 0, 0, 0, 0, 0, 0, false, true));
        verify(summarizer, never()).summarize(any());
    }
}
