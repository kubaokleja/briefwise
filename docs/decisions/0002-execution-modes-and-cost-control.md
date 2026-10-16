# 0002. Execution modes and cost control

- Status: Accepted
- Date: 2026-10-16

## Context

The project must cost <= 5 CHF/month in steady state and must not cost money while nobody uses it
([DESIGN 12](../DESIGN.md#12-non-functional-requirements), [13](../DESIGN.md#13-cost-control)). The same use
case (e.g. news ingest) must be reachable on demand, from a scheduler and from a CI job. LLM calls are the
main variable cost, and tests must be deterministic and free.

## Options

Execution toggling:
1. **`@ConfigurationProperties` + `@ConditionalOnProperty`**: modes and per-job `enabled` flags in
   `application.yml`. No dependency.
2. **Feature-flag library (Unleash, FF4J)**: runtime toggles, UI. Extra service/dependency, overkill for one user.
3. **Toggles stored in the database**: runtime changes without restart. More code; no current need.

LLM provider per environment:
1. Always `anthropic`: simplest, but CI/dev/demo cost money and are non-deterministic.
2. **`ollama` / `anthropic` / `replay` selected by profile**.
3. Mock LLM only in tests: does not cover the demo or repeated dev runs.

Spend limits:
1. In-app budget only. 2. Anthropic Console limit only. 3. **Both** (in-app daily token budget per feature,
Console monthly cap as last line of defence).

## Decision

- Execution via three inbound adapters over the same facades: admin endpoint (always), in-app scheduler
  (`briefwise.execution.mode=scheduled` only), job runner (`job` profile). Per-job flags
  `briefwise.jobs.<name>.enabled`. No feature-flag library; DB toggles only if a real need appears.
- `briefwise.llm.provider` = `ollama` (local dev; never for evals) | `anthropic` (evals, jobs, on-demand) |
  `replay` (CI, demo). `replay` is a `ChatModel` decorator keyed by `hash(model, prompt_version, normalized
  input)` over `ai_generation_log`; `on-miss` is `fail` in CI/demo and `fallback` locally.
- Cheap model (Haiku) for classification, stronger model only for commentary; model names only in
  `application.yml`. Prompt caching of the stable prefix; Message Batches API for evals/backfills.
- Code gate before every LLM call; regeneration gate for briefs. Briefs are on demand by default.
- Hard caps: daily token budget per feature in `aiops` (`UsageLimitExceededException` -> HTTP 429 or skipped
  job step) plus a monthly spend limit in the Anthropic Console.
- Tests never call a live LLM: WireMock recordings and `replay`.

## Consequences

- CI, demo and repeated dev runs are free and deterministic; the generation log doubles as a cache.
- `ai_generation_log` growth needs a retention policy (30 days full text; replay fixtures exported to
  `evals/replay/`).
- Fidelity risk: `replay` and `ollama` do not prove production quality; real quality is measured only by
  `anthropic` evals run on demand.
- Config surface grows; mitigated by a single typed `BriefwiseProperties`.
- Revisit if an always-on host is introduced (then `mode=scheduled` becomes relevant).
