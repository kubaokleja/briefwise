# 0020. Classification model confirmed by the first eval: Haiku 5.5, run committed as baseline

- Status: Accepted
- Date: 2026-11-07

## Context

[ADR 0012](0012-news-classification-llm-call.md) chose `claude-haiku-5-5` for news classification (L2,
[DESIGN 8](../DESIGN.md#8-ai-and-engineering-decision-points)) as a starting point, to be confirmed by the
classification eval ([ADR 0018](0018-classification-eval.md)). The first live eval ran on 2026-11-07.

**Dataset:** the golden set `evals/sentiment_golden.csv.gpg`, 258 hand-labelled Finnhub headlines (150 random, 108
stratified), labelled before any model output, drafted by Claude and spot-checked by the owner (label-bias caveat in
[DESIGN 10](../DESIGN.md#10-evaluation-strategy)). **Run:** `claude-haiku-5-5`, prompt `v1`, Message Batches API,
batch `msgbatch_01NEYjzTSHdRuxTG8jWRDVHU`.

| Label | Macro F1 (all) | Macro F1 (random) | Accuracy (all) |
|---|---|---|---|
| Sentiment (gated) | 0.785 | 0.853 | 0.860 |
| Relevance | 0.676 | 0.658 | 0.748 |
| Event type | 0.572 | 0.424 | 0.640 |

Cost 0.0118 USD for the run (batch price), 0.046 USD per 1,000 items; production (standard price) about twice that.
5 of 258 answers (1.9%) unusable: empty `affectedTickers` on generic items. Errors: sentiment leans to `NEUTRAL`
(positive recall 0.61, negative 0.74, no positive/negative swaps); relevance over-predicts `HIGH` (31 vs 18);
event type over-predicts `PRODUCT` (38 `OTHER` items).

## Options

1. **Keep Haiku 5.5, commit this run as `baseline.json`.** Good sentiment at the lowest price; the gate starts now.
   The weak labels look like prompt issues (`PRODUCT` vs `OTHER`, empty tickers) rather than model capacity.
2. **Compare Sonnet 5.5 first** (~0.24 USD at batch price). Evidence from two models, but 20x the running cost for
   an unknown gain, and it pulls part of the M6 eval matrix forward.
3. **Keep Haiku, improve the prompt (v2) before setting a baseline.** Better reference, but the regression gate
   stays inactive until then.

## Decision

Option 1 (owner's choice, 2026-11-07). `claude-haiku-5-5` stays the classification model;
`evals/results/news-classification/baseline.json` is this run. The gate compares sentiment macro F1 against 0.785
with the 3 pp tolerance from ADR 0018.

## Consequences

- Sentiment errors are mostly "missed signal" (positive or negative read as neutral), the cheaper error for an RM
  feed than a wrong direction. Over-predicted `HIGH` relevance shows more news rather than hiding some.
- Follow-ups (PLANNING): the empty `affectedTickers` answers (allow none for generic items, or enforce at least one
  in the schema) and the `PRODUCT`/`OTHER` boundary; both mean a prompt `v2` and a new baseline run.
- The label-bias caveat stands: Claude-drafted labels may flatter a Claude model. A human-reviewed subset (M5) is the
  check.
- Revisit with the M6 eval matrix (Sonnet 5.5, Haiku without few-shot, Alpha Vantage baseline), or if the gate starts
  failing on a prompt change.
