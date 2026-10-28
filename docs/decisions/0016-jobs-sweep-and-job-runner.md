# 0016. Jobs, sweep of stuck news and the job runner

- Status: Accepted
- Date: 2026-10-28

## Context

M1 slice 6 adds the inbound adapters of [DESIGN 13.2](../DESIGN.md#132-execution-modes-feature-toggles) (admin
"run now" endpoint, in-app scheduler, job runner for GitHub Actions per [ADR 0003](0003-runtime-github-actions-and-neon.md))
and closes four questions left open by earlier slices:

- An item whose `news.raw` message ends in the DLT, or whose message was never consumed before the broker
  disappeared, stays `RECEIVED` with a published outbox row; nothing re-sends it ([ADR 0011](0011-news-outbox-relay-key-and-idempotency.md),
  [DESIGN 7.1](../DESIGN.md#71-news-pipeline-m1)). A budget-deferred item stays `RECEIVED` too ([ADR 0012](0012-news-classification-llm-call.md)).
- A provider outage turns every item of a run into `UNCLASSIFIED` (`PROVIDER_ERROR`) for good.
- When is a job runner "done"? DESIGN says: when consumer lag is 0 or after `max-duration`, but not how to measure lag
  or what exit code a timeout gets.
- The admin endpoint should require role `ADMIN`, but login only arrives with M4.

## Options

**Stuck items**
1. *Sweep of old `RECEIVED` items*, through the same `NewsProcessing.process` as the consumer (one code gate,
   budget, screening). Needs an age threshold so it never races the relay or an in-flight consumer, and a retry cap
   so a poison item is not swept forever (new column).
2. *DLT replay tool* that re-publishes `news.raw.DLT`. Only covers the DLT case, not lost or deferred messages, and in
   GitHub Actions the DLT is gone with the broker.

**Provider errors**
1. *Retry*: reopen recent `PROVIDER_ERROR` items (conditional `UNCLASSIFIED` → `RECEIVED`) in the sweep, under the same
   cap. Costs < 1 cent per outage day at Haiku prices; a terminal state becomes non-terminal for this one reason.
2. *Final*: the item stays in the feed without labels. Simplest, but one outage loses a day of labels.

**Done condition**
1. *Kafka consumer lag* via the `Admin` client in kafka-clients (no new dependency): end offset minus committed
   offset of group `briefwise-news`. Exact; DLT'd records are committed too.
2. *Database condition* ("no recent `RECEIVED` items"): no broker query, but budget-deferred items never leave
   `RECEIVED`, so the run would wait until the timeout.

**Admin endpoint before M4**
1. *Open until M4*: the app only runs locally (ADR 0003), the daily budget caps cost (ADR 0008). No dependency.
2. *Only in `local`*: no real Anthropic calls from `cloud` via HTTP; cloud runs need the runner.
3. *Spring Security with HTTP Basic now*: pulls M4 forward, and every web test has to deal with security.

## Decision

- **Sweep = `NewsFacade.classifyPending`**: reopen `PROVIDER_ERROR` items classified within
  `provider-error-retry-window` (48h) that have fewer than `max-attempts` sweeps; then pick `RECEIVED` items created
  more than `min-age` (10 min) ago that have no unpublished outbox row, oldest first, `batch-size` 200. Each pick
  counts one attempt (`news_items.sweep_attempts`, Flyway V14) *before* processing. An item picked with attempts
  above `max-attempts` (3) becomes `UNCLASSIFIED` with the new reason `RETRIES_EXHAUSTED`. The run stops at the first
  deferral, since the budget is per feature and day, so a deferral costs at most one attempt per run. A per-item
  exception is counted and the sweep goes on. `INVALID_OUTPUT` and `INJECTION_SUSPECTED` are never retried.
- **Jobs**: `news-ingest` = ingest → publish until nothing is pending → (runner only) wait until consumer lag is 0 →
  sweep. `news-classify` = sweep. A failing step is recorded and the run goes on; a timed-out wait skips the sweep.
  A used-up budget (`UsageLimitExceededException`) marks a step `SKIPPED`, which is not a failure.
- **Done = consumer lag 0**, polled every `lag-poll-interval` (2s) until `briefwise.job-runner.max-duration` (15m).
- **Runner exit codes**: 0 done (or job disabled), 1 a step failed or timed out, 2 unknown or malformed job id.
  `BriefwiseApplication` exits with the code after startup (the Kafka listener threads would otherwise keep the JVM
  alive). The runner and scheduler honour `briefwise.jobs.<id>.enabled`; the admin endpoint ignores it.
- **Admin endpoint `POST /api/admin/jobs/{job}/run` is open until M4**, synchronous, 200 with the run status (404
  unknown job, 400 malformed id). M4 adds role `ADMIN`.
- **Neon secrets** follow `application-cloud.yml`: `NEON_JDBC_URL`, `NEON_DB_USER`, `NEON_DB_PASSWORD`.
- The stopgap `NewsStartupIngest` (`briefwise.news.ingest.on-startup`) is removed; the admin endpoint replaces it.

## Consequences

- Nothing stuck in `RECEIVED` is lost: DLT'd, never-consumed and deferred items are classified by a later run or end
  visibly as `RETRIES_EXHAUSTED`. Items the consumer DLT'd in the current run wait for the next run (10 min min-age).
- A provider outage costs one retry per item (up to 3 sweeps) instead of a day of labels.
- A slow or failed run is visible: GitHub emails on exit 1. Leftovers stay in Postgres for the next run.
- The lag query opens a short-lived `Admin` client per poll (~450 polls max per run); fine at this scale.
- The admin endpoint is unauthenticated until M4: do not expose the app beyond localhost before then.
- Revisit the thresholds if the sweep regularly gives up on items, or if runs often hit `max-duration`.
