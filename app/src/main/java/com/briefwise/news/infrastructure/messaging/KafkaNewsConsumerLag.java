package com.briefwise.news.infrastructure.messaging;

import com.briefwise.news.domain.port.NewsConsumerLag;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Consumer lag of the {@code news.raw} listener group, asked from the broker with the Kafka {@link Admin} client: per
 * partition, the end offset minus the group's committed offset. A partition the group has not committed yet counts
 * from its earliest offset, as the consumer does ({@code auto-offset-reset: earliest}). A record recovered to the DLT
 * is committed too, so the lag reaches 0 once every message has been handled one way or the other.
 */
@Component
class KafkaNewsConsumerLag implements NewsConsumerLag {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final KafkaAdmin kafkaAdmin;

    KafkaNewsConsumerLag(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public long lag() {
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            var partitions = await(admin.describeTopics(List.of(NewsRawMessages.TOPIC)).allTopicNames())
                    .get(NewsRawMessages.TOPIC).partitions().stream()
                    .map(p -> new TopicPartition(NewsRawMessages.TOPIC, p.partition()))
                    .toList();
            Map<TopicPartition, OffsetAndMetadata> committed = await(
                    admin.listConsumerGroupOffsets(NewsRawMessages.CONSUMER_GROUP).partitionsToOffsetAndMetadata());
            Map<TopicPartition, ListOffsetsResultInfo> earliest = offsets(admin, partitions, OffsetSpec.earliest());
            Map<TopicPartition, ListOffsetsResultInfo> latest = offsets(admin, partitions, OffsetSpec.latest());
            long lag = 0;
            for (TopicPartition partition : partitions) {
                OffsetAndMetadata commit = committed.get(partition);
                long position = commit != null ? commit.offset() : earliest.get(partition).offset();
                lag += Math.max(0, latest.get(partition).offset() - position);
            }
            return lag;
        }
    }

    private static Map<TopicPartition, ListOffsetsResultInfo> offsets(Admin admin,
                                                                      List<TopicPartition> partitions,
                                                                      OffsetSpec spec) {
        Map<TopicPartition, OffsetSpec> request = partitions.stream()
                .collect(Collectors.toMap(Function.identity(), p -> spec));
        return await(admin.listOffsets(request).all());
    }

    private static <T> T await(KafkaFuture<T> future) {
        try {
            return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while asking Kafka for the news.raw consumer lag", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka did not answer the news.raw consumer lag query", e);
        }
    }
}
