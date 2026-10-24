package com.briefwise.news.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code news.raw} message format (K2, ADR 0011): only ids, the consumer loads the current item. Key = ticker.
 * Producer and consumer use this one class, so the format cannot drift between them.
 */
@Component
class NewsRawMessages {

    static final String TOPIC = "news.raw";
    static final String DEAD_LETTER_TOPIC = "news.raw.DLT";
    static final String CORRELATION_HEADER = "X-Correlation-ID";

    /** {@code messageId} is the outbox row id: the same for every re-send of one row. */
    record NewsRawMessage(UUID messageId, UUID newsId, Instant occurredAt) {}

    private final JsonMapper json;

    NewsRawMessages(JsonMapper json) {
        this.json = json;
    }

    String write(NewsRawMessage message) {
        return json.writeValueAsString(message);
    }

    /** @throws MalformedNewsMessageException if the value is not a complete message; retrying cannot help */
    NewsRawMessage read(String value) {
        NewsRawMessage message;
        try {
            message = json.readValue(value, NewsRawMessage.class);
        } catch (JacksonException e) {
            throw new MalformedNewsMessageException("Not a news.raw message: " + e.getOriginalMessage(), e);
        }
        if (message == null || message.messageId() == null || message.newsId() == null) {
            throw new MalformedNewsMessageException("news.raw message without messageId or newsId", null);
        }
        return message;
    }

    static class MalformedNewsMessageException extends RuntimeException {

        MalformedNewsMessageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
