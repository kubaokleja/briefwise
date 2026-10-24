package com.briefwise.news.infrastructure.messaging;

import com.briefwise.news.domain.event.NewsIngested;
import com.briefwise.news.domain.model.OutboxMessage;
import com.briefwise.news.domain.port.NewsOutbox;
import com.briefwise.news.infrastructure.messaging.NewsRawMessages.NewsRawMessage;
import com.briefwise.shared.infrastructure.web.CorrelationIdFilter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code news_outbox} over plain JDBC: the relay needs {@code FOR UPDATE SKIP LOCKED}, and no code reads outbox rows
 * as entities. Appending and locking require the caller's transaction ({@code MANDATORY}), so an outbox row can
 * never be committed without its news item.
 */
@Component
class NewsOutboxAdapter implements NewsOutbox {

    private final JdbcTemplate jdbc;
    private final NewsRawMessages messages;

    NewsOutboxAdapter(JdbcTemplate jdbc, NewsRawMessages messages) {
        this.jdbc = jdbc;
        this.messages = messages;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(NewsIngested event) {
        UUID id = UUID.randomUUID();
        String payload = messages.write(new NewsRawMessage(id, event.newsId(), event.occurredAt()));
        jdbc.update("""
                INSERT INTO news_outbox (id, aggregate_id, topic, message_key, payload, correlation_id)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)""",
                id, event.newsId(), NewsRawMessages.TOPIC, event.ticker().symbol(), payload,
                MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxMessage> lockPending(int limit) {
        return jdbc.query("""
                SELECT id, topic, message_key, payload::text AS payload, correlation_id FROM news_outbox
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED""",
                (rs, row) -> new OutboxMessage(rs.getObject("id", UUID.class), rs.getString("topic"),
                        rs.getString("message_key"), rs.getString("payload"),
                        Optional.ofNullable(rs.getString("correlation_id"))),
                limit);
    }

    @Override
    @Transactional
    public void markPublished(UUID id, Instant publishedAt) {
        jdbc.update("UPDATE news_outbox SET published_at = ?, updated_at = now() WHERE id = ?",
                Timestamp.from(publishedAt), id);
    }

    @Override
    @Transactional
    public void recordFailure(UUID id, String error) {
        jdbc.update("UPDATE news_outbox SET attempts = attempts + 1, last_error = ?, updated_at = now() WHERE id = ?",
                error, id);
    }

    @Override
    @Transactional(readOnly = true)
    public int countPending() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM news_outbox WHERE published_at IS NULL",
                Integer.class);
        return count == null ? 0 : count;
    }
}
