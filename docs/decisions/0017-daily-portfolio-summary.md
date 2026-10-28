# 0017. Daily portfolio summary: model, trigger, selection, regeneration gate and M1 compliance check

- Status: Accepted
- Date: 2026-10-28

## Context

M1 slice 7 adds the second LLM feature: a short daily summary of the classified news for each portfolio
([DESIGN 7.1](../DESIGN.md#71-news-pipeline-m1)). It is the first feature that produces text for an RM, so it also
brings the first compliance text check ([DESIGN 11](../DESIGN.md#11-security-and-compliance): no recommendations, no
forecasts, citations, a fixed disclaimer) and the AI marker / `isFallback` states ([DESIGN 15](../DESIGN.md#15-ui-design)).
Open points from PLANNING: S1 (model, trigger), the M1 scope of `compliance.checkText`, and the domain question
whether stale `RECEIVED` items still matter (2026-10-28). Budget: <= 5 CHF/month
([DESIGN 13](../DESIGN.md#13-cost-control)). The adapter follows the `llm-adapter` skill
([ADR 0012](0012-news-classification-llm-call.md), [0015](0015-replay-provider.md)).

## Options

**Model (S1)**
1. Haiku 5.5: about 0.001 USD per call (~1k input, ~200 output tokens), about 0.03 USD/month for 3 portfolios.
   Good enough to summarise items that are already labelled.
2. Sonnet 5.5: better synthesis, about 0.01-0.02 USD per call (~0.7 USD/month). Needs a per-call model override and
   takes temperature instead of effort.
3. Haiku now, with a separate model property so an eval can switch it later.

**Trigger**
1. A pipeline step after the sweep, plus a read-only GET. The summary is ready in the morning, and a page view never
   calls the LLM.
2. On demand only: cheaper on days nobody looks, but the first view waits and the cloud job never pre-builds it.
3. Both: one more endpoint to secure in M4.

**`checkText` scope and handling a violation**
1. Deterministic rules in code, one re-ask that names the flagged phrases, then a template fallback.
2. Deterministic rules, flag only (store the text with its violations).
3. Deterministic rules plus an LLM judge now: more coverage, extra cost per summary, and it needs calibration
   ([DESIGN 10](../DESIGN.md#10-evaluation-strategy)).

**Pattern style**
1. Match advice phrasing in the text's own voice ("investors should consider selling", "will rise",
   "likely to outperform"). Reported analyst actions ("downgraded to Sell", "raised its price target") pass.
2. Match bare words (buy/sell/hold/target/will): safest, but most analyst news would trigger the fallback.

**Selection**
1. HIGH and MEDIUM, published in the last 24 h, sorted by relevance then portfolio weight, at most 15.
2. HIGH only: most days would be empty.
3. Everything classified since the last summary: covers weekends and outages, but a stale item would show up as
   today's news.

**Few-shot / caching**
1. No few-shot. The ~450-token system prompt is below Haiku's 512-token cache minimum, so it is not cached. At one
   call per portfolio per day, a 5-minute cache would rarely hit anyway.
2. One synthetic example: steadier style and a cacheable prefix, at about +400 input tokens per call.

## Decision

- **Model**: Haiku 5.5, through the same chat model bean as classification, with its own feature `news-summary`
  (generation log, replay key) and budget `briefwise.aiops.daily-budget-usd.news-summary: 0.05`. Effort `low`,
  `max-tokens` 2048 (adaptive thinking plus up to 5 points of JSON), `max-attempts` 2 (one re-ask after invalid
  output).
- **Trigger**: a `summarize` step after the sweep in the `news-ingest` job (`NewsFacade.summarizePortfolios`). It is
  `SKIPPED` on timeout or when a deferral (budget, no model, replay miss) stops the run.
  `GET /api/portfolios/{id}/news/summary` only reads.
- **Output**: native structured output into `SummaryAnswer { points: [{ text, newsIds }] }`, at most 5 points.
  Invalid output means no points, more than 5, a point without text or ids, or an id that was not in the request.
  Citations are built in code from the cited ids (`Citation(NEWS, newsId, url, headline)`), so the URL and
  headline never come from the model.
- **Prompt data**: per item the id, held affected tickers, publication date, relevance, sentiment, event type,
  headline and summary (cut to 300 characters). Only fields already screened at classification and our own labels
  go in. No rationale (model output that was never screened), no portfolio name, client or weight. Items come in
  importance order; rendering is deterministic, so the replay key is stable.
- **Compliance**: `ComplianceFacade.checkText` with deterministic rules (`TextRules.M1`). `NO_RECOMMENDATION` and
  `NO_FORECAST` use advice phrasing (pattern option 1). `CITATION_REQUIRED` means at least one citation per point.
  `DISCLAIMER_REQUIRED` means the full text contains the fixed disclaimer. The disclaimer is **appended in code**
  and stored with the summary ("AI-generated summary of third-party news for internal use by the relationship
  manager. Not investment advice and not a recommendation to buy, sell or hold any security. Check the cited
  sources before use.").
- **On a violation**: one re-ask, with a `<corrections>` block naming each rule and phrase. If the re-ask still
  violates, fails or defers, the template is stored: one point per item, "TICKER: headline (sentiment, event type)",
  with `isFallback`. The template is checked too, and anything it still breaks (e.g. a quoted headline) is stored
  and shown with it. Invalid output twice or a provider error also lead to the template. A deferral on the first
  call keeps the previous summary.
- **Selection** (`getRelevantNews`, `SelectionPolicy`): CLASSIFIED items with at least one *affected* ticker held
  by the portfolio and relevance MEDIUM or higher, published within 24 h. Sorted by relevance, then by the largest
  held weight among the affected tickers, then newest; at most 15 (`briefwise.news.selection.*`). This is the
  portfolio weighting deferred by [ADR 0014](0014-classification-output-shape.md).
- **Regeneration gate** (`RegenerationGate`): a new summary is made if none exists for today (UTC); if today's is
  a template or empty and the selection now has items; or if a HIGH item in the selection was classified after
  today's summary was made. A new MEDIUM item alone does not regenerate. An empty selection stores an empty summary
  with no LLM call.
- **Storage**: `news_portfolio_summaries` (V15), one row per portfolio and UTC date, upserted. Points and
  violations are stored as JSONB.
- **Stale `RECEIVED` items** (domain question 2026-10-28): summaries exclude them through the 24 h published
  window. Classification is unchanged. Briefs decide at M4.
- **Few-shot**: none (option 1).

## Consequences

- Cost per day stays far below the 0.05 USD budget: at most one call per portfolio per run, plus re-asks.
- The rules are a first layer. Paraphrased advice ("now looks attractive") passes; the M4 LLM evaluator and the RM's
  approval are the next layers. False positives cost one re-ask and, at worst, a template summary, never a
  non-compliant text.
- A model summary can still state facts that are not in the cited item. Citation by id only proves *which* items
  were used. Number grounding comes with the brief eval ([DESIGN 10](../DESIGN.md#10-evaluation-strategy)).
- The template quotes third-party headlines, which can trip the rules. Those violations are stored and shown.
- A ticker the classifier did not mark as affected never reaches a portfolio (an item fetched for AAPL but about
  MSFT is shown to MSFT holders only).
- Fixtures: `news-summary` replay recordings join the open recording run (`evals/replay/README.md`).
- Revisit the model and few-shot once a summary eval exists (LLM-as-judge plus a manual sample), and the window
  when the morning brief (M4) defines its inputs.
