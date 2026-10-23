package com.briefwise.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.model.DedupPolicy;
import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.news.domain.model.HeldInstrument;
import com.briefwise.news.domain.model.HeldInstrument.Kind;
import com.briefwise.news.domain.model.IngestPolicy;
import com.briefwise.news.domain.model.IngestSummary;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsSourceUnavailableException;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.news.domain.port.HeldInstruments;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.NewsSource;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NewsIngestServiceTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker SPY = new Ticker("SPY");
    private static final DedupPolicy DEDUP = new DedupPolicy(0.6, Duration.ofHours(48));
    // 01:30 in Zurich is still the previous day in UTC: the window must use the UTC date.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T23:30:00Z"), ZoneId.of("Europe/Zurich"));
    private static final LocalDate TODAY_UTC = LocalDate.parse("2026-10-20");

    private final HeldInstruments held = mock(HeldInstruments.class);
    private final NewsSource source = mock(NewsSource.class);
    private final NewsRepository repository = mock(NewsRepository.class);
    private final NewsIngestService service =
            new NewsIngestService(held, source, repository, new IngestPolicy(2, DEDUP), CLOCK);

    private static FetchedNews article(Ticker ticker, String headline, String url, String publishedAt) {
        return new FetchedNews(ticker, headline, "", url, Instant.parse(publishedAt), "Reuters");
    }

    @BeforeEach
    void noDuplicatesByDefault() {
        when(repository.findIdByUrlHash(any())).thenReturn(Optional.empty());
        when(repository.findNearDuplicate(any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void funds_are_skipped_and_companies_queried_over_the_utc_lookback_window() {
        when(held.list()).thenReturn(List.of(new HeldInstrument(AAPL, Kind.COMPANY), new HeldInstrument(SPY, Kind.FUND)));
        when(source.fetch(AAPL, TODAY_UTC.minusDays(2), TODAY_UTC)).thenReturn(List.of());

        IngestSummary summary = service.ingest();

        assertThat(summary.queried()).containsExactly(AAPL);
        assertThat(summary.skipped()).containsExactly(SPY);
        verify(source).fetch(AAPL, TODAY_UTC.minusDays(2), TODAY_UTC);
        verify(source, never()).fetch(eq(SPY), any(), any());
    }

    @Test
    void new_article_is_stored_as_received() {
        when(held.list()).thenReturn(List.of(new HeldInstrument(AAPL, Kind.COMPANY)));
        FetchedNews news = article(AAPL, "Apple beats estimates", "https://example.com/1", "2026-10-20T10:00:00Z");
        when(source.fetch(any(), any(), any())).thenReturn(List.of(news));

        IngestSummary summary = service.ingest();

        ArgumentCaptor<NewsItem> stored = ArgumentCaptor.forClass(NewsItem.class);
        verify(repository).insert(stored.capture(), any());
        assertThat(stored.getValue().status()).isEqualTo(NewsStatus.RECEIVED);
        assertThat(stored.getValue().headline()).isEqualTo("Apple beats estimates");
        assertThat(summary.fetched()).isEqualTo(1);
        assertThat(summary.inserted()).isEqualTo(1);
    }

    @Test
    void known_url_only_gets_the_ticker_added() {
        UUID existing = UUID.randomUUID();
        when(held.list()).thenReturn(List.of(new HeldInstrument(MSFT, Kind.COMPANY)));
        when(source.fetch(any(), any(), any())).thenReturn(
                List.of(article(MSFT, "Apple and Microsoft sign deal", "https://example.com/1", "2026-10-20T10:00:00Z")));
        when(repository.findIdByUrlHash(UrlHash.of("https://example.com/1"))).thenReturn(Optional.of(existing));

        IngestSummary summary = service.ingest();

        verify(repository).addTicker(existing, MSFT);
        verify(repository, never()).insert(any(), any());
        assertThat(summary.alreadyKnown()).isEqualTo(1);
    }

    @Test
    void near_duplicate_is_stored_as_skipped_pointing_to_the_original() {
        UUID original = UUID.randomUUID();
        when(held.list()).thenReturn(List.of(new HeldInstrument(AAPL, Kind.COMPANY)));
        FetchedNews news = article(AAPL, "Apple beats estimates, shares up", "https://example.com/2", "2026-10-20T11:00:00Z");
        when(source.fetch(any(), any(), any())).thenReturn(List.of(news));
        when(repository.findNearDuplicate(AAPL, news.headline(), news.publishedAt(), DEDUP)).thenReturn(Optional.of(original));

        IngestSummary summary = service.ingest();

        ArgumentCaptor<NewsItem> stored = ArgumentCaptor.forClass(NewsItem.class);
        verify(repository).insert(stored.capture(), any());
        assertThat(stored.getValue().status()).isEqualTo(NewsStatus.SKIPPED);
        assertThat(stored.getValue().duplicateOf()).contains(original);
        assertThat(summary.nearDuplicates()).isEqualTo(1);
        assertThat(summary.inserted()).isZero();
    }

    @Test
    void articles_are_stored_oldest_first_so_the_earliest_becomes_the_original() {
        when(held.list()).thenReturn(List.of(new HeldInstrument(AAPL, Kind.COMPANY)));
        FetchedNews newer = article(AAPL, "Newer", "https://example.com/new", "2026-10-20T12:00:00Z");
        FetchedNews older = article(AAPL, "Older", "https://example.com/old", "2026-10-19T12:00:00Z");
        when(source.fetch(any(), any(), any())).thenReturn(List.of(newer, older)); // Finnhub: newest first

        service.ingest();

        ArgumentCaptor<NewsItem> stored = ArgumentCaptor.forClass(NewsItem.class);
        verify(repository, times(2)).insert(stored.capture(), any());
        assertThat(stored.getAllValues()).extracting(NewsItem::headline).containsExactly("Older", "Newer");
    }

    @Test
    void a_failing_ticker_is_reported_and_the_run_goes_on() {
        when(held.list()).thenReturn(List.of(new HeldInstrument(AAPL, Kind.COMPANY), new HeldInstrument(MSFT, Kind.COMPANY)));
        when(source.fetch(any(), any(), any())).thenAnswer(call -> {
            if (call.getArgument(0).equals(AAPL)) {
                throw new NewsSourceUnavailableException(AAPL, "HTTP 503", null);
            }
            return List.of(article(MSFT, "Microsoft news", "https://example.com/m", "2026-10-20T10:00:00Z"));
        });

        IngestSummary summary = service.ingest();

        assertThat(summary.failed()).containsExactly(AAPL);
        assertThat(summary.queried()).containsExactly(MSFT);
        assertThat(summary.inserted()).isEqualTo(1);
    }

    @Test
    void misconfiguration_stops_the_run() {
        when(held.list()).thenReturn(List.of(new HeldInstrument(AAPL, Kind.COMPANY)));
        when(source.fetch(any(), any(), any())).thenThrow(new IllegalStateException("No Finnhub API key"));

        assertThatThrownBy(service::ingest).isInstanceOf(IllegalStateException.class);
    }
}
