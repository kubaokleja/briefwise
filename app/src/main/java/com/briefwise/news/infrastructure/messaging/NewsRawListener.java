package com.briefwise.news.infrastructure.messaging;

import com.briefwise.news.NewsFacade;
import com.briefwise.news.infrastructure.messaging.NewsRawMessages.NewsRawMessage;
import com.briefwise.shared.infrastructure.web.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for {@code news.raw}: parse, put the correlation ID of the ingest run in the MDC (a new one if the
 * message has none), call the facade. Idempotency lives behind the facade (K3); retries and the DLT in
 * {@link NewsKafkaConfig}.
 */
@Component
class NewsRawListener {

    private final NewsFacade news;
    private final NewsRawMessages messages;

    NewsRawListener(NewsFacade news, NewsRawMessages messages) {
        this.news = news;
        this.messages = messages;
    }

    @KafkaListener(topics = NewsRawMessages.TOPIC, groupId = "briefwise-news",
            containerFactory = NewsKafkaConfig.LISTENER_FACTORY,
            autoStartup = "${briefwise.news.kafka.consumer-enabled:true}")
    void onMessage(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(NewsRawMessages.CORRELATION_HEADER);
        String correlationId = header == null
                ? UUID.randomUUID().toString()
                : new String(header.value(), StandardCharsets.UTF_8);
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        try {
            NewsRawMessage message = messages.read(record.value());
            news.processIngested(message.newsId());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
