# 0014. Classification output shape: relevance per item, ANALYST event type, generation log subject

- Status: Accepted
- Date: 2026-10-24

## Context

Four domain questions were left for M1 slice 4 (PLANNING, Domain Questions Log): what `Relevance` is relative to;
whether the classifier answers per item or per ticker; whether `EventType` needs values for analyst actions and
customer contracts; and whether `ai_generation_log` should point at the object a call was made for. The labelling
convention ([ADR 0010](0010-golden-set-labelling.md), `evals/labelling/README.md`) already labels per item from the
view of the most affected ticker.

## Options

**Relevance reference**
1. General materiality for a holder of the ticker: one call per item, matches the labels.
2. Per portfolio (weight, mandate): one call per item x portfolio, and the prompt would carry portfolio data.

**Output granularity**
1. Per item: one sentiment/relevance/event type, plus the affected tickers (a subset of the item's tickers).
2. Per ticker: a list of labels per ticker; more output tokens, labels do not match the golden set.

**Event types**
1. Add `ANALYST` (rating and price-target changes, about 20 labelled rows) and keep contracts in `PRODUCT`/`OTHER`.
2. Add both `ANALYST` and `CONTRACT`.
3. Add none; keep them in `OTHER`.

**Generation log subject**
1. Nullable `subject_id` (UUID) on `ai_generation_log`; the `feature` already says what kind of object it is.
2. No reference; link through feature + correlation ID only.

## Decision

- Relevance option 1. Portfolio weighting happens in code when news is selected for a portfolio (slice 7), without
  more LLM calls.
- Output option 1. The affected tickers are stored as a flag on `news_item_tickers`. A ticker the item does not
  have is invalid output (re-ask, then fallback).
- Event types option 1. The labelled rows noted as analyst actions are relabelled `OTHER` -> `ANALYST` in the local
  CSV (2026-10-24).
- Generation log option 1: V11 adds `subject_id` with an index; the news adapter sets it to the news item id.

## Consequences

- An RM may see a HIGH item for a ticker that is a tiny position. Slice 7 sorts or filters by weight.
- Per-ticker nuance (good for one holder, bad for another) is lost on multi-ticker items; revisit if the eval shows
  many such items.
- Audits can answer "which call produced this classification?" with one indexed query.
