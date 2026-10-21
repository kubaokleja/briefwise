# 0008. LLM daily budget: unit, gate shape and day boundary

- Status: Accepted
- Date: 2026-10-21

## Context

[ADR 0002](0002-execution-modes-and-cost-control.md) chose two hard caps: an in-app daily budget per feature
(`aiops`) and the Anthropic Console monthly limit. It left open what the in-app budget is measured in, how the gate
sits around a call, and when a "day" starts (B1 in M1 slice 1). DESIGN names only `checkAndRecordUsage`, called
after each call ([12.1](../DESIGN.md#121-engineering-conventions)), while the code gate in
[7.1](../DESIGN.md#71-news-pipeline-m1) needs a "budget left" check before the call. Budget: <= 5 CHF/month
([13](../DESIGN.md#13-cost-control)).

## Options

**Unit**
1. Tokens per day. Independent of price changes, but a Sonnet 5.5 token costs 20x a Haiku 5.5 token, so equal
   limits mean very different money.
2. **USD per day.** Matches the real constraint and compares across models. Depends on correct prices in config.
3. Both. Safest, but double the configuration and test cases for little gain at this scale.

**Gate shape**
1. Post-call only (`checkAndRecordUsage` throws once over budget). Literal DESIGN, but the "budget left" code gate
   has nothing to call, and throwing after a successful call discards a paid-for result.
2. **Pre-check + post-record.** `ensureBudgetAvailable` before (throws), `checkAndRecordUsage` after (records,
   never throws). Can overshoot by at most the calls already in flight.
3. Reserve up front (estimate from `maxTokens`, settle after). Never overshoots, but needs reservations and
   rollback on failure.

**Day boundary**
1. **UTC date.** GitHub Actions cron is UTC; no DST.
2. Europe/Zurich date. Matches the RM's day; DST edge cases in tests.

## Decision

- **USD per feature per UTC day**, configured in `briefwise.aiops.daily-budget-usd.<feature>`. Starting value:
  `news-classification: 0.10` (estimate ~0.03/day for ~150 items; worst case ~2.2 USD/month over 22 weekdays).
- **Pre-check + post-record**: `AiopsFacade.ensureBudgetAvailable(feature)` throws `UsageLimitExceededException`
  (HTTP 429 `ProblemDetail` with `feature`; job step skipped from slice 6). `checkAndRecordUsage(feature, model,
  tokens)` adds the call atomically (`INSERT ... ON CONFLICT DO UPDATE`) to `ai_daily_usage` and returns the
  status, including `exhausted`.
- **Fail closed**: a feature without a budget or a model without a price (`briefwise.aiops.pricing`, USD per 1M
  input/output tokens) throws rather than spending or logging a cost of 0.
- Costs are stored as `NUMERIC(14, 8)` USD.

## Consequences

- One budget table works for every model; switching classification to Sonnet makes the cap bite sooner, which is
  intended.
- Prices are copied by hand from Anthropic's pricing page and go stale; the yml comment carries the check date.
  The Console limit remains the backstop.
- Overshoot of one call per concurrent caller is accepted (a classification call costs ~0.0002 USD).
- Prompt-cache tokens (cache write/read prices) are not modelled yet: `TokenCount` has input/output only. Extend it
  together with prompt caching (L5, M1 slice 4). Haiku 5.5's higher rate for prompts above 100K tokens is not
  modelled; classification prompts are far below it.
- Revisit if a per-month cap or per-portfolio budgets are needed, or when a feature runs concurrently at volume
  (then consider reservations, option 3).
