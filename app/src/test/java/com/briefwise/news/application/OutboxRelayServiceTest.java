package com.briefwise.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.model.MessagePublishException;
import com.briefwise.news.domain.model.OutboxMessage;
import com.briefwise.news.domain.model.RelayPolicy;
import com.briefwise.news.domain.model.RelaySummary;
import com.briefwise.news.domain.port.NewsMessagePublisher;
import com.briefwise.news.domain.port.NewsOutbox;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

class OutboxRelayServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T07:00:00Z"), ZoneOffset.UTC);

    private final NewsOutbox outbox = mock(NewsOutbox.class);
    private final NewsMessagePublisher publisher = mock(NewsMessagePublisher.class);
    private final OutboxRelayService relay = new OutboxRelayService(outbox, publisher,
            TransactionOperations.withoutTransaction(), new RelayPolicy(2), CLOCK);

    private static OutboxMessage message() {
        return new OutboxMessage(UUID.randomUUID(), "news.raw", "AAPL", "{}", Optional.empty());
    }

    @Test
    void publishes_each_row_and_marks_it_published_after_the_send() {
        OutboxMessage first = message();
        when(outbox.lockPending(2)).thenReturn(List.of(first));

        RelaySummary summary = relay.relayPending();

        InOrder order = inOrder(publisher, outbox);
        order.verify(publisher).publish(first);
        order.verify(outbox).markPublished(first.id(), CLOCK.instant());
        assertThat(summary.published()).isEqualTo(1);
        assertThat(summary.brokerUnavailable()).isFalse();
    }

    @Test
    void claims_further_batches_while_batches_come_back_full() {
        when(outbox.lockPending(2)).thenReturn(List.of(message(), message()), List.of(message()));

        RelaySummary summary = relay.relayPending();

        verify(outbox, times(2)).lockPending(2);
        assertThat(summary.published()).isEqualTo(3);
    }

    @Test
    void a_failed_send_stops_the_run_and_leaves_the_rows_pending() {
        OutboxMessage first = message();
        OutboxMessage second = message();
        when(outbox.lockPending(2)).thenReturn(List.of(first, second));
        doThrow(new MessagePublishException("broker down", null)).when(publisher).publish(first);
        when(outbox.countPending()).thenReturn(2);

        RelaySummary summary = relay.relayPending();

        verify(outbox).recordFailure(eq(first.id()), any());
        verify(outbox, never()).markPublished(any(), any());
        verify(publisher, never()).publish(second);
        verify(outbox, times(1)).lockPending(anyInt()); // no further batch after a failure
        assertThat(summary).isEqualTo(new RelaySummary(0, 2, true));
    }

    @Test
    void the_next_run_sends_what_the_failed_run_left() {
        OutboxMessage pending = message();
        when(outbox.lockPending(2)).thenReturn(List.of(pending));
        doThrow(new MessagePublishException("broker down", null)).doNothing().when(publisher).publish(pending);

        assertThat(relay.relayPending().brokerUnavailable()).isTrue();
        RelaySummary second = relay.relayPending();

        verify(outbox).markPublished(pending.id(), CLOCK.instant());
        assertThat(second.published()).isEqualTo(1);
    }
}
