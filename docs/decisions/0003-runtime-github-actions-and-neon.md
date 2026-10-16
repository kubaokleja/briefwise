# 0003. Runtime: GitHub Actions and Neon

- Status: Accepted
- Date: 2026-10-16

## Context

Briefwise needs a daily pipeline (quant batch, news ingest + classification, risk alerts) but has one user and
a budget of <= 5 CHF/month ([DESIGN 13.1](../DESIGN.md#131-no-always-on-infrastructure),
[14](../DESIGN.md#14-runtime-cicd-and-demo)). An always-on server would be idle almost all day. Kafka and
Postgres must exist during the pipeline run; the demo must work offline.

## Options

1. **GitHub Actions (scheduled) + Neon Postgres (free)**: pay-per-use, no idle server. Cron is UTC and may be
   delayed; Kafka only lives during the run; scheduled workflows in public repos are disabled after 60 days of
   inactivity; Neon free tier has 0.5 GB per project and a compute quota.
2. **Oracle Always Free Ampere VM**: always-on, free. Capacity is limited, idle instances can be reclaimed,
   terms have changed without notice; needs hardening (Tailscale only).
3. **Paid VPS**: simple and reliable, but a recurring cost and an idle machine.

## Decision

Option 1. Scheduled work runs in GitHub Actions (`morning-pipeline.yml`, active only when repo variable
`PIPELINE_ENABLED == 'true'`) against Neon. Kafka runs as a service container for the workflow duration.
Postgres is the source of truth: news is persisted with a transactional outbox, and unconsumed items are
re-published on the next run. The app (`job` profile) runs and exits when consumer lag is 0 or `max-duration` is
reached. The app and UI otherwise run locally (`local`, `cloud`, `demo` profiles). Secrets only in GitHub Secrets;
fork PRs never receive secrets. An optional live demo on Oracle is deferred (decide at F7).

## Consequences

- Near-zero infrastructure cost and no server to maintain or secure.
- Schedule is approximate: use a margin (e.g. `30 4 * * 1-5`) and never rely on minute precision.
- The outbox pattern is required, not optional: the broker disappears after each run.
- Neon storage limit forces a retention policy for `ai_generation_log`; MLflow may need a separate Neon project.
- Not a real-time system: briefs and Q&A require a locally running app.
- Revisit if the repository's activity stops (60-day rule), free-tier limits change, or an always-on need appears.
