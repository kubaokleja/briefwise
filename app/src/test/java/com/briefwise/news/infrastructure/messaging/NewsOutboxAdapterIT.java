package com.briefwise.news.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.domain.event.NewsIngested;
import com.briefwise.news.domain.model.OutboxMessage;
import com.briefwise.news.domain.port.NewsOutbox;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** The outbox table against real Postgres: transaction requirement, pending order, {@code SKIP LOCKED}. */
class NewsOutboxAdapterIT extends AbstractIntegrationTest {

    private static final Ticker AAPL = new Ticker("AAPL");

    @Autowired
    NewsOutbox outbox;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM news_outbox");
    }

    private UUID append() {
        UUID newsId = UUID.randomUUID();
        transactions.executeWithoutResult(s -> outbox.append(new NewsIngested(newsId, AAPL, Instant.now())));
        return newsId;
    }

    private List<OutboxMessage> lockPending(int limit) {
        return transactions.execute(s -> outbox.lockPending(limit));
    }

    @Test
    void append_outside_a_transaction_is_refused() {
        assertThatThrownBy(() -> outbox.append(new NewsIngested(UUID.randomUUID(), AAPL, Instant.now())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void appended_row_carries_topic_ticker_key_and_ids() {
        UUID newsId = append();

        OutboxMessage message = lockPending(10).getFirst();

        assertThat(message.topic()).isEqualTo("news.raw");
        assertThat(message.key()).isEqualTo("AAPL");
        assertThat(message.payload()).contains(newsId.toString()).contains(message.id().toString());
    }

    @Test
    void pending_rows_come_oldest_first_and_published_ones_are_left_out() {
        UUID first = append();
        UUID second = append();
        UUID third = append();
        OutboxMessage published = lockPending(1).getFirst();
        outbox.markPublished(published.id(), Instant.now());

        List<OutboxMessage> pending = lockPending(10);

        assertThat(published.payload()).contains(first.toString());
        assertThat(pending).extracting(OutboxMessage::payload)
                .satisfiesExactly(p -> assertThat(p).contains(second.toString()),
                        p -> assertThat(p).contains(third.toString()));
        assertThat(outbox.countPending()).isEqualTo(2);
    }

    @Test
    void rows_locked_by_one_relay_are_skipped_by_another() {
        append();
        append();

        List<OutboxMessage> seenByOther = transactions.execute(s -> {
            List<OutboxMessage> mine = outbox.lockPending(1); // held until this transaction ends
            List<OutboxMessage> other = CompletableFuture.supplyAsync(() -> lockPending(10)).join();
            assertThat(other).doesNotContainAnyElementsOf(mine);
            return other;
        });

        assertThat(seenByOther).hasSize(1);
    }

    @Test
    void failure_is_counted_and_the_row_stays_pending() {
        append();
        OutboxMessage message = lockPending(1).getFirst();

        outbox.recordFailure(message.id(), "broker down");

        Map<String, Object> row = jdbc.queryForMap("SELECT attempts, last_error, published_at FROM news_outbox");
        assertThat(row).containsEntry("attempts", 1).containsEntry("last_error", "broker down")
                .containsEntry("published_at", null);
    }
}
