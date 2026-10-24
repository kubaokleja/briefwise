package com.briefwise.news.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.news.infrastructure.messaging.NewsRawMessages.MalformedNewsMessageException;
import com.briefwise.news.infrastructure.messaging.NewsRawMessages.NewsRawMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NewsRawMessagesTest {

    private final NewsRawMessages messages = new NewsRawMessages(JsonMapper.builder().build());

    @Test
    void written_message_reads_back_unchanged() {
        NewsRawMessage message = new NewsRawMessage(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-10-24T07:00:00Z"));

        assertThat(messages.read(messages.write(message))).isEqualTo(message);
    }

    @Test
    void value_that_is_not_json_is_malformed() {
        assertThatThrownBy(() -> messages.read("not json")).isInstanceOf(MalformedNewsMessageException.class);
    }

    @Test
    void message_without_news_id_is_malformed() {
        assertThatThrownBy(() -> messages.read("{\"messageId\":\"" + UUID.randomUUID() + "\"}"))
                .isInstanceOf(MalformedNewsMessageException.class);
    }
}
