package com.briefwise.news.application;

import com.briefwise.news.domain.model.MessagePublishException;
import com.briefwise.news.domain.model.OutboxMessage;
import com.briefwise.news.domain.model.RelayPolicy;
import com.briefwise.news.domain.model.RelaySummary;
import com.briefwise.news.domain.port.NewsMessagePublisher;
import com.briefwise.news.domain.port.NewsOutbox;
import com.briefwise.news.domain.port.OutboxRelay;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Polling outbox relay (K1, ADR 0011). Each batch is one transaction: claim rows ({@code SKIP LOCKED}), send each and
 * wait for the ack, mark it published. A crash after the ack but before the commit re-sends the batch on the next
 * run: delivery is at least once, and the consumer is idempotent.
 */
@Service
class OutboxRelayService implements OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private record Batch(int claimed, int published, boolean failed) {}

    private final NewsOutbox outbox;
    private final NewsMessagePublisher publisher;
    private final TransactionOperations transactions;
    private final RelayPolicy policy;
    private final Clock clock;

    OutboxRelayService(NewsOutbox outbox, NewsMessagePublisher publisher, TransactionOperations transactions,
                       RelayPolicy policy, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.transactions = transactions;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public RelaySummary relayPending() {
        int published = 0;
        boolean failed = false;
        while (true) {
            Batch batch = transactions.execute(status -> relayBatch());
            published += batch.published();
            if (batch.failed()) {
                failed = true;
                break;
            }
            if (batch.claimed() < policy.batchSize()) {
                break; // nothing left, or the rest is locked by another relay
            }
        }
        RelaySummary summary = new RelaySummary(published, outbox.countPending(), failed);
        if (summary.published() > 0 || failed) {
            log.info("Outbox relay: {}", summary);
        }
        return summary;
    }

    private Batch relayBatch() {
        List<OutboxMessage> messages = outbox.lockPending(policy.batchSize());
        int published = 0;
        for (OutboxMessage message : messages) {
            try {
                publisher.publish(message);
            } catch (MessagePublishException e) {
                // The broker is probably gone; the rest would fail the same way. Keep them for the next run.
                log.warn("Outbox relay stopped at {}: {}", message.id(), e.getMessage());
                outbox.recordFailure(message.id(), e.getMessage());
                return new Batch(messages.size(), published, true);
            }
            outbox.markPublished(message.id(), clock.instant());
            published++;
        }
        return new Batch(messages.size(), published, false);
    }
}
