# 0021. Job runs: provider-error window, sweep status, run deadline and one run at a time

- Status: Accepted
- Date: 2026-11-07

Amends [ADR 0016](0016-jobs-sweep-and-job-runner.md) in the four points below; the rest of ADR 0016 stands.

## Context

The jobs and sweep walkthroughs (2026-11-07, PLANNING Domain Questions Log) found four gaps to close before the
scheduled pipeline is switched on (`PIPELINE_ENABLED`, [ADR 0003](0003-runtime-github-actions-and-neon.md)):

1. The scheduled runner runs Mon-Fri at 04:30 UTC, but `provider-error-retry-window` was 48 h: a `PROVIDER_ERROR`
   item from Friday is about 72 h old on Monday and was never retried.
2. The `sweep` step was `SUCCEEDED` even when every picked item threw (per-item exceptions are counted, not thrown),
   so the runner exited 0 and GitHub sent no email, although ADR 0016 promises that failed runs are visible. A sweep
   stopped on a budget deferral was also `SUCCEEDED`, while `summarize` maps the same case to `SKIPPED`.
3. `max-duration` (15 min) bounded only publish and drain. The sweep (up to 200 sequential LLM calls) and the
   summaries had no deadline, so a slow run was killed by the workflow's `timeout-minutes` without the app's exit 1.
4. Nothing stopped two runs from overlapping (admin + admin, admin + scheduler); GitHub serialises only its own
   workflow runs. Two sweeps pick the same items and count their attempts twice.

## Options

**1. Retry window:** (a) 96 h; (b) no time window, the attempt cap only; (c) accept.
(b) is simpler but reopens week-old items long after they matter; (c) loses every Friday outage item.

**2. Sweep status:** (a) any failure `FAILED`; (b) `FAILED` when at least half of the picked items failed;
(c) keep failures green. (a) turns a run red for one poison item (at most 3 runs, then it gives up); (b) mails only
on a systematic fault and keeps isolated failures in the log; (c) leaves a broken sweep invisible.
In all options a deferral becomes `SKIPPED`, like the summaries.

**3. Deadline:** (a) sweep and summaries stop between items at the deadline; (b) every step including ingest;
(c) no code, document `max-duration` as a wait limit. Ingest is 36 HTTP calls with their own timeouts and retries,
so (b) adds code for little; (c) keeps the workflow timeout as the real limit, without a clean exit code.

**4. Overlap:** (a) Postgres advisory lock; (b) accept until M4/M6. A lock table would need a migration and
stale-lock clean-up; an advisory lock is released by Postgres when the session ends, also if the process dies.

## Decision

Owner's choices (2026-11-07): 1a, 2b, 3a, 4a.

- `briefwise.news.sweep.provider-error-retry-window: 96h` (default too): a weekend plus one missed run; the cap of
  3 attempts still bounds the retries.
- `sweep` step: `FAILED` when `failed > 0` and `2 x failed >= picked`; `TIMED_OUT` when it stopped at the deadline;
  `SKIPPED` when it stopped on a deferral; otherwise `SUCCEEDED` (isolated failures are logged and retried).
- `NewsFacade.classifyPending(deadline)` and `summarizePortfolios(deadline)` stop before the next item or portfolio
  once the deadline (run start + `max-duration`) has passed and report `stoppedOnDeadline`; the step is then
  `TIMED_OUT` and the runner exits 1. A timed-out sweep skips the summaries. Ingest is not bounded.
- Port `JobLock`, adapter `PostgresJobLock`: `pg_try_advisory_lock(hashtext('briefwise.job.' || lockGroup))` on a
  pooled connection held for the run. `news-ingest` and `news-classify` share the lock group `news-pipeline` (both
  sweep); the eval has its own. A second run returns at once with one `lock` step `SKIPPED`, so the runner exits 0.

## Consequences

- A Friday provider outage is retried on Monday; a systematic sweep fault now mails; a long run ends with the app's
  exit code and a "next run" message instead of the workflow timeout.
- Two concurrent sweeps through the jobs are no longer possible. A sweep can still overlap the Kafka consumer (by
  design, conditional writes; Domain Questions Log 2026-11-07 on double LLM calls).
- One pooled connection is held per running job; the pool (default 10) is far larger than the number of jobs.
- A skipped second run is green (exit 0): a scheduled run that finds the previous one still going is not an error.
- The threshold leaves a minority of failing items green; watch `failed` in the runner output during the first
  Neon runs (ADR 0016 thresholds).
