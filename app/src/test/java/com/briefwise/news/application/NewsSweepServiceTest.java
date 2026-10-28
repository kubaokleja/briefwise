package com.briefwise.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.model.ProcessingOutcome;
import com.briefwise.news.domain.model.SweepPolicy;
import com.briefwise.news.domain.model.SweepSummary;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.port.NewsProcessing;
import com.briefwise.news.domain.port.NewsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsSweepServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T05:00:00Z");
    private static final SweepPolicy POLICY = new SweepPolicy(Duration.ofMinutes(10), 200, 3, Duration.ofHours(48));

    private final NewsRepository repository = mock(NewsRepository.class);
    private final NewsProcessing processing = mock(NewsProcessing.class);
    private final NewsSweepService service = new NewsSweepService(repository, processing, POLICY,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private List<UUID> sweepable(int count) {
        List<UUID> ids = java.util.stream.Stream.generate(UUID::randomUUID).limit(count).toList();
        when(repository.findSweepable(NOW.minus(Duration.ofMinutes(10)), 200)).thenReturn(ids);
        ids.forEach(id -> when(repository.incrementSweepAttempts(id)).thenReturn(1));
        return ids;
    }

    @Test
    void reopens_recent_provider_errors_then_processes_old_received_items_oldest_first() {
        when(repository.reopenProviderErrors(NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(48)), 3))
                .thenReturn(2);
        List<UUID> ids = sweepable(3);
        when(processing.process(ids.get(0))).thenReturn(ProcessingOutcome.CLASSIFIED);
        when(processing.process(ids.get(1))).thenReturn(ProcessingOutcome.UNCLASSIFIED);
        when(processing.process(ids.get(2))).thenReturn(ProcessingOutcome.NOT_HELD);

        SweepSummary summary = service.classifyPending();

        assertThat(summary).isEqualTo(new SweepSummary(2, 3, 1, 1, 1, 0, 0, 0, 0, false));
    }

    @Test
    void stops_at_the_first_deferral_since_the_rest_would_defer_too() {
        List<UUID> ids = sweepable(3);
        when(processing.process(ids.get(0))).thenReturn(ProcessingOutcome.CLASSIFIED);
        when(processing.process(ids.get(1))).thenReturn(ProcessingOutcome.PENDING);

        SweepSummary summary = service.classifyPending();

        assertThat(summary.picked()).isEqualTo(2);
        assertThat(summary.pending()).isEqualTo(1);
        assertThat(summary.stoppedOnDeferral()).isTrue();
        verify(processing, never()).process(ids.get(2));
        verify(repository, never()).incrementSweepAttempts(ids.get(2));
    }

    @Test
    void item_over_the_attempt_cap_gives_up_without_processing() {
        List<UUID> ids = sweepable(1);
        UUID poison = ids.getFirst();
        when(repository.incrementSweepAttempts(poison)).thenReturn(4);
        when(repository.markUnclassifiedFromReceived(poison, UnclassifiedReason.RETRIES_EXHAUSTED, NOW))
                .thenReturn(true);

        SweepSummary summary = service.classifyPending();

        assertThat(summary.gaveUp()).isEqualTo(1);
        verify(processing, never()).process(any());
    }

    @Test
    void the_last_allowed_attempt_still_processes() {
        UUID id = sweepable(1).getFirst();
        when(repository.incrementSweepAttempts(id)).thenReturn(3);
        when(processing.process(id)).thenReturn(ProcessingOutcome.CLASSIFIED);

        assertThat(service.classifyPending().classified()).isEqualTo(1);
    }

    @Test
    void a_failing_item_is_counted_and_the_sweep_goes_on() {
        List<UUID> ids = sweepable(2);
        when(processing.process(ids.get(0))).thenThrow(new IllegalStateException("poison"));
        when(processing.process(ids.get(1))).thenReturn(ProcessingOutcome.CLASSIFIED);

        SweepSummary summary = service.classifyPending();

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.classified()).isEqualTo(1);
    }

    @Test
    void an_item_a_consumer_finished_meanwhile_is_counted_as_already_processed() {
        UUID id = sweepable(1).getFirst();
        when(processing.process(id)).thenReturn(ProcessingOutcome.ALREADY_PROCESSED);

        assertThat(service.classifyPending().alreadyProcessed()).isEqualTo(1);
    }
}
