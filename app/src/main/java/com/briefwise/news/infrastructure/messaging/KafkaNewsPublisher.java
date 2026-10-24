package com.briefwise.news.infrastructure.messaging;

import com.briefwise.news.domain.model.MessagePublishException;
import com.briefwise.news.domain.model.OutboxMessage;
import com.briefwise.news.domain.port.NewsMessagePublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends one outbox row and blocks until the broker acknowledges it ({@code acks=all}), so the relay marks a row
 * published only once Kafka has it. The producer's own timeouts ({@code max.block.ms}, {@code delivery.timeout.ms})
 * are set below {@code send-timeout} in {@code application.yml}, so a missing broker fails fast.
 */
@Component
class KafkaNewsPublisher implements NewsMessagePublisher {

    private final KafkaTemplate<String, String> kafka;
    private final Duration sendTimeout;

    KafkaNewsPublisher(KafkaTemplate<String, String> kafka, NewsKafkaProperties properties) {
        this.kafka = kafka;
        this.sendTimeout = properties.sendTimeout();
    }

    @Override
    public void publish(OutboxMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(message.topic(), message.key(), message.payload());
        message.correlationId().ifPresent(id -> record.headers()
                .add(NewsRawMessages.CORRELATION_HEADER, id.getBytes(StandardCharsets.UTF_8)));
        try {
            kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException("Interrupted while sending " + message.id(), e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            throw new MessagePublishException("Broker did not acknowledge " + message.id() + ": " + cause, cause);
        }
    }
}
