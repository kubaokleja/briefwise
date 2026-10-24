package com.briefwise.news.infrastructure.messaging;

import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.infrastructure.messaging.NewsRawMessages.MalformedNewsMessageException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Topics and the error handling of the {@code news.raw} listener (ADR 0011). The listener gets its own container
 * factory, so this retry/DLT policy does not silently apply to listeners other contexts add later.
 */
@Configuration(proxyBeanMethods = false)
class NewsKafkaConfig {

    static final String LISTENER_FACTORY = "newsKafkaListenerContainerFactory";

    /** Created by Spring's {@code KafkaAdmin} at startup if missing (single broker locally and in CI: 1 replica). */
    @Bean
    NewTopic newsRawTopic(NewsKafkaProperties properties) {
        return TopicBuilder.name(NewsRawMessages.TOPIC).partitions(properties.partitions()).replicas(1).build();
    }

    @Bean
    NewTopic newsRawDeadLetterTopic(NewsKafkaProperties properties) {
        return TopicBuilder.name(NewsRawMessages.DEAD_LETTER_TOPIC).partitions(properties.partitions()).replicas(1)
                .build();
    }

    /**
     * {@code max-attempts} deliveries in total, then the record goes to {@code news.raw.DLT} on the same partition
     * and its offset is committed. A malformed message or an unknown item goes there at once: retrying cannot help.
     */
    @Bean(LISTENER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<Object, Object> newsKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumers,
            KafkaTemplate<String, String> kafka, NewsKafkaProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, e) -> new TopicPartition(NewsRawMessages.DEAD_LETTER_TOPIC, record.partition()));
        var errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryBackoff().toMillis(), properties.maxAttempts() - 1L));
        errorHandler.addNotRetryableExceptions(MalformedNewsMessageException.class, NewsItemNotFoundException.class);

        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumers);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
