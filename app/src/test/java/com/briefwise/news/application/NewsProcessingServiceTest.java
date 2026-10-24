package com.briefwise.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.ProcessingOutcome;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.port.NewsClassifier;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsProcessingServiceTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final NewsClassification RESULT = new NewsClassification(Sentiment.POSITIVE, Relevance.HIGH,
            EventType.EARNINGS, Set.of(AAPL), "Earnings beat.", "test-v1");

    private final NewsRepository repository = mock(NewsRepository.class);
    private final NewsClassifier classifier = mock(NewsClassifier.class);
    private final NewsProcessingService service = new NewsProcessingService(repository, classifier);

    private NewsItem stored(NewsStatus status) {
        NewsItem item = new NewsItem(UUID.randomUUID(), "Apple beats estimates", "", "https://example.com/1",
                Instant.parse("2026-10-24T06:00:00Z"), "Reuters", Set.of(AAPL), status, Optional.empty());
        when(repository.findById(item.id())).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void received_item_is_classified_and_moved_on_conditionally() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(classifier.classify(item)).thenReturn(Optional.of(RESULT));
        when(repository.transitionFromReceived(item.id(), NewsStatus.CLASSIFIED)).thenReturn(true);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.CLASSIFIED);
    }

    @Test
    void item_no_longer_received_is_a_duplicate_delivery_and_not_classified_again() {
        NewsItem item = stored(NewsStatus.CLASSIFIED);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.ALREADY_PROCESSED);
        verifyNoInteractions(classifier);
        verify(repository, never()).transitionFromReceived(any(), any());
    }

    @Test
    void a_concurrent_delivery_that_wrote_first_wins() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(classifier.classify(item)).thenReturn(Optional.of(RESULT));
        when(repository.transitionFromReceived(item.id(), NewsStatus.CLASSIFIED)).thenReturn(false);

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.ALREADY_PROCESSED);
    }

    @Test
    void no_classifier_result_leaves_the_item_received() {
        NewsItem item = stored(NewsStatus.RECEIVED);
        when(classifier.classify(item)).thenReturn(Optional.empty());

        assertThat(service.process(item.id())).isEqualTo(ProcessingOutcome.PENDING);
        verify(repository, never()).transitionFromReceived(any(), any());
    }

    @Test
    void unknown_item_is_reported() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(unknown)).isInstanceOf(NewsItemNotFoundException.class);
    }
}
