# 0011. News outbox relay (K1), Kafka key (K2) and consumer idempotency (K3)

- Status: Accepted
- Date: 2026-10-24

## Context

[DESIGN 7.1](../DESIGN.md#71-news-pipeline-m1) sends stored news items to Kafka `news.raw` through a transactional
outbox: Postgres is the source of truth, and in GitHub Actions the broker lives only for one workflow run
([ADR 0003](0003-runtime-github-actions-and-neon.md)). Three points were open: how outbox rows reach Kafka
([DESIGN 18](../DESIGN.md#18-open-questions): polling relay vs Debezium), which key a news item with several tickers
gets ([DESIGN 6.4](../DESIGN.md#64-domain-events-and-kafka-topics) says "ticker"), and how the consumer recognises a
message it has already handled. An outbox relay delivers at least once (a crash between send and "mark published"
re-sends), so duplicates are expected, and from slice 4 every non-idempotent delivery can cost an LLM call.

A news item's ticker set can grow after it is stored: a known URL fetched for another ticker only adds a row to
`news_item_tickers` ([ADR 0009](0009-news-dedup-and-etf-news.md)).

## Options

**K1: relay**
1. Polling relay in the app: claim pending rows (`FOR UPDATE SKIP LOCKED`), send, wait for the broker ack, set
   `published_at`. About a hundred lines of our code, no extra infrastructure, latency of one poll interval; the job
   runner calls the same method.
2. Debezium CDC: reads the Postgres WAL via logical replication; near-zero latency and no polling, but needs an
   always-on Kafka Connect process (conflicts with ADR 0003 and the 5 CHF budget) and replication slots on Neon.

**K2: key for multi-ticker news**
1. One message per item, key = the ticker it was first fetched for, payload = `newsId` only; the consumer reads the
   current tickers from the database. Matches DESIGN 6.4, needs no extra column (the key is known at insert).
2. One message per (item, ticker): only useful if classification runs per ticker (open, slice 4); multiplies
   messages and later LLM calls.
3. Key = `newsId`: even spread, but no per-ticker ordering and deviates from DESIGN.

**K3: idempotency store**
1. Status check: act only while the item is `RECEIVED`; the result is written with
   `UPDATE ... WHERE status = 'RECEIVED'`, so the first writer wins. No new table; the status is already the state
   machine. A duplicate delivered to a second consumer during a rebalance can still trigger a second LLM call.
2. Processed-messages table `(consumer, message_id)` written in the effect's transaction: generic and reusable by
   any consumer, but one more table and write per message, and it duplicates what the status already says.

## Decision

- **K1: polling relay** (option 1). Rows are marked published only after the ack, so delivery is at least once.
- **K2: option 1.** One `news.raw` message per item, key = first-fetched ticker, payload carries only `newsId` (and
  the outbox id as message id); consumers load the current item.
- **K3: status check with conditional write** (option 1). The rare double LLM call after a rebalance is accepted
  (fractions of a cent); data stays correct because only the first write succeeds.
- Outbox rows only for `RECEIVED` items; `SKIPPED` near-duplicates get none. The consumer gate still checks status.
- Item and outbox row are inserted in one transaction.
- Consumer errors: Spring Kafka `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`, 3 attempts 1 s apart
  (fixed backoff), then `news.raw.DLT`. Records that cannot be deserialized go to the DLT without retries.
- Published outbox rows are kept for now; retention is added to the M6 retention task.

## Consequences

- No new runtime component; the relay works the same in `local`, `job` and tests, and nothing is lost when the
  broker is gone: unpublished rows are sent on the next run.
- Polling adds one indexed query per interval (partial index on pending rows) and up to one interval of latency,
  irrelevant for a morning batch.
- The status-based idempotency is specific to news; the `kafka-consumer` skill must state that a consumer without
  a natural state machine needs option 2.
- Ordering per ticker holds only for the first-fetched ticker; nothing in M1 depends on ordering.
- Revisit K2 if slice 4 decides classification per ticker, and K1 if latency ever matters (Debezium).
