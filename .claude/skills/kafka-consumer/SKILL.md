---
name: kafka-consumer
description: Use when adding a Kafka topic, producer or consumer to the Briefwise app (e.g. "consume news.classified", "publish risk alerts to Kafka"). Covers the transactional outbox, the polling relay, keys, idempotent consumers, retries and the dead-letter topic, and the Testcontainers Kafka test. Pattern first implemented by hand for news.raw (ADR 0011).
---

# Kafka producer / consumer

Reference implementation: `news` context, topic `news.raw` ([ADR 0011](../../../docs/decisions/0011-news-outbox-relay-key-and-idempotency.md)).
Copy its shape; do not copy rules from CLAUDE.md here. A new topic only when a second consumer actually needs it
([DESIGN 6.4](../../../docs/DESIGN.md#64-domain-events-and-kafka-topics)): ask first, add it to the DESIGN 6.4 table.

## Decide first (ask me, record in an ADR if it differs from ADR 0011)

1. **Key**: what needs ordering together? (news: the first-fetched ticker.)
2. **Payload**: ids only, consumer loads current state (default), or a full snapshot (only if the consumer must
   not read the producer's tables).
3. **Idempotency (K3)**: does the effect have a natural state machine with a conditional write
   (`UPDATE ... WHERE status = 'X'`)? If not, use a processed-messages table written in the effect's transaction.
4. **Retry policy**: attempts, backoff, which exceptions are not retryable.

## Producer side: transactional outbox

1. **Domain event** record in `domain/event/` (pure Java). The application service appends it in the **same
   transaction** as the state change (`TransactionOperations` in the service; no direct `KafkaTemplate` call).
2. **Outbox table** `{context}_outbox` via the `flyway-migration` skill (copy `V10__news_outbox.sql`: id = message id,
   aggregate id, topic, key, JSONB payload, correlation id, attempts, last error, `published_at`, partial index
   on pending rows). No FK to the aggregate.
3. **Ports** in `domain/port/`: `{Context}Outbox` (append / lockPending / markPublished / recordFailure /
   countPending) and a publisher port. Adapter in `infrastructure/messaging/` over `JdbcTemplate`;
   `append` and `lockPending` are `@Transactional(propagation = MANDATORY)`.
4. **Relay**: package-private `OutboxRelayService` (batch per transaction, `FOR UPDATE SKIP LOCKED`, mark
   published only after the ack, stop at the first failed send). Exposed through the facade
   (`publishPending()`); the scheduler and job runner only call the facade.
5. **Message format** in one package-private class shared by producer and consumer (`NewsRawMessages`): topic
   names, record, JSON via the Boot `JsonMapper` (Jackson 3), String key/value serializers.
6. Send the correlation ID as the `X-Correlation-ID` header.

## Consumer side

1. `@KafkaListener` in `infrastructure/messaging/` with an explicit `groupId` and its **own container factory**
   (`ConcurrentKafkaListenerContainerFactoryConfigurer.configure` + `setCommonErrorHandler`), so its retry policy
   does not leak into other listeners. Put the header's correlation ID in the MDC; remove it in `finally`.
2. The listener parses and calls the **facade**; no logic. Idempotency lives in the application service.
3. `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` to `{topic}.DLT`, same partition; DLT topic created
   with the same partition count (`NewTopic` beans). Malformed messages and "unknown aggregate" are
   `addNotRetryableExceptions`.
4. Properties under `briefwise.{context}.kafka.*`; test profile: relay off, backoff `10ms`.

## Tests

- Unit: relay service (order, batches, failure leaves rows pending, next run sends them), processing service
  (duplicate, concurrent writer, unknown aggregate), message format round trip + malformed.
- `*IT` (shared context from `AbstractIntegrationTest`, Kafka via `TestcontainersConfig`): outbox adapter (transaction
  required, order, `SKIP LOCKED`), and `{Context}KafkaIT`: relay → consumed, duplicate delivery → one effect (use a
  same-key marker message to know both copies were processed), failing handler → N attempts → DLT, malformed and
  unknown → DLT without retries. The real exception class is in the `kafka_dlt-exception-cause-fqcn` header.
- Replace adapters for ITs with a recording fake imported in `AbstractIntegrationTest` (one Spring context, one set
  of containers); never `@MockitoBean` per class.
- Clean the outbox table in `@BeforeEach` of every IT that writes it.

## Verify

`./mvnw verify` in `app/` (Docker needed). Update `FlywayBaselineIT` migration count. Tick PLANNING.
