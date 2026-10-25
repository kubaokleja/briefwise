package com.briefwise.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.event.NewsClassified;
import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.HeldInstrument;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.ProcessingOutcome;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.port.HeldInstruments;
import com.briefwise.news.domain.port.NewsClassifier;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.UntrustedContentScreen;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class NewsProcessingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T07:00:00Z");
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final NewsClassification RESULT = new NewsClassification(Sentiment.POSITIVE, Relevance.HIGH,
            EventType.EARNINGS, Set.of(AAPL), "Earnings beat.", "test-v1");

    private final NewsRepository repository = mock(NewsRepository.class);
    private final HeldInstruments held = mock(HeldInstruments.class);
    private final UntrustedContentScreen screen = mock(UntrustedContentScreen.class);
    private final NewsClassifier classifier = mock(NewsClassifier.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NewsProcessingService service = new NewsProcessingService(repository, held, screen, classifier,
            events, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void aaplIsHeld() {
        when(held.list()).thenReturn(List.of(new HeldInstrument(AAPL, HeldInstrument.Kind.COMPANY)));
    }

    private NewsItem stored(NewsStatus status, Ticker... tickers) {
        Set<Ticker> set = tickers.length == 0 ? Set.of(AAPL) : Set.of(tickers);
        NewsItem item = new NewsItem(UUID.randomUUID(), "Apple beats estimates", "", "https://example.com/1",
                Instant.parse("2026-10-24T06:00:00Z"), "Reuters", set, status, Optional.empty());
        when(repository.findById(item.id())).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void received_item_is_classified_stored_conditionally_and_announced() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(classifier.classify(item)).thenReturn(new ClassifierResult.Classified(RESULT));
        when(repository.classifyFromReceived(item.id(), RESULT, NOW)).thenReturn(true);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.CLASSIFIED);
        verify(events).publishEvent(new NewsClassified(item.id(), Relevance.HIGH, NOW));
    }

    @Test
    void item_no_longer_received_is_a_duplicate_delivery_and_not_classified_again() {
        NewsItem item = stored(NewsStatus.CLASSIFIED);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.ALREADY_PROCESSED);
        verifyNoInteractions(classifier, screen);
        verify(repository, never()).classifyFromReceived(any(), any(), any());
    }

    @Test
    void a_concurrent_delivery_that_wrote_first_wins_and_no_event_is_sent() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(classifier.classify(item)).thenReturn(new ClassifierResult.Classified(RESULT));
        when(repository.classifyFromReceived(item.id(), RESULT, NOW)).thenReturn(false);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.ALREADY_PROCESSED);
        verifyNoInteractions(events);
    }

    @Test
    void item_whose_tickers_are_no_longer_held_is_skipped_without_an_llm_call() {
        NewsItem item = stored(NewsStatus.RECEIVED, new Ticker("TSLA"));
        when(repository.skipFromReceived(item.id())).thenReturn(true);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.NOT_HELD);
        verifyNoInteractions(classifier, screen);
    }

    @Test
    void one_held_ticker_is_enough_to_classify() {
        NewsItem item = stored(NewsStatus.RECEIVED, AAPL, new Ticker("TSLA"));
        when(classifier.classify(item)).thenReturn(new ClassifierResult.Classified(RESULT));
        when(repository.classifyFromReceived(item.id(), RESULT, NOW)).thenReturn(true);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.CLASSIFIED);
    }

    @Test
    void suspected_injection_never_reaches_the_classifier() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(screen.isSuspected(item)).thenReturn(true);
        when(repository.markUnclassifiedFromReceived(item.id(), UnclassifiedReason.INJECTION_SUSPECTED, NOW))
                .thenReturn(true);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.UNCLASSIFIED);
        verifyNoInteractions(classifier);
    }

    @Test
    void failed_classification_falls_back_to_unclassified_with_the_reason() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(classifier.classify(item)).thenReturn(new ClassifierResult.Failed(UnclassifiedReason.INVALID_OUTPUT));
        when(repository.markUnclassifiedFromReceived(item.id(), UnclassifiedReason.INVALID_OUTPUT, NOW))
                .thenReturn(true);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.UNCLASSIFIED);
        verifyNoInteractions(events);
    }

    @Test
    void deferred_classification_leaves_the_item_received() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(classifier.classify(item)).thenReturn(new ClassifierResult.Deferred("budget used up"));

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.PENDING);
        verify(repository, never()).classifyFromReceived(any(), any(), any());
        verify(repository, never()).markUnclassifiedFromReceived(any(), any(), any());
        verify(repository, never()).skipFromReceived(any());
    }

    @Test
    void unknown_item_is_reported() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(unknown)).isInstanceOf(NewsItemNotFoundException.class);
    }
}
