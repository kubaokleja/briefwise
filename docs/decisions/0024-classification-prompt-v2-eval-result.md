# 0024. Classification prompt v2 measured and adopted as the eval baseline

- Status: Accepted
- Date: 2026-11-14

## Context

[ADR 0023](0023-classification-prompt-v2-scope.md) set the scope of prompt `v2`: an empty `affectedTickers` list only
for `LOW` items, and a narrower `PRODUCT` definition with one synthetic `OTHER` example. It stated that the `v2` run
becomes the baseline only if the owner accepts it. This ADR records the measurement and that decision. It does not
change ADR 0020's model choice (Haiku 5.5).

**Dataset:** the same golden set as the v1 baseline, 258 rows (150 random), `goldenSha256` `a80c98dd…` (comparable,
[ADR 0022](0022-eval-baseline-comparability.md)). **Run:** `claude-haiku-5-5`, prompt `v2`, Message Batches API,
batch `msgbatch_01Hh7HbySAHjNB6vJKSH4eGe`, run locally with profiles `job,eval` on a throwaway database
(2026-11-14). Cost 0.0119 USD (v1: 0.0118).

| Label (macro F1) | v1, all | v2, all | v1, random | v2, random |
|---|---|---|---|---|
| Sentiment (gated) | 0.785 | **0.809** (+2.4 pp) | 0.853 | 0.804 (-5.0 pp) |
| Relevance | 0.676 | 0.657 (-2.0 pp) | 0.658 | 0.646 (-1.2 pp) |
| Event type | 0.572 | **0.584** (+1.3 pp) | 0.424 | **0.473** (+4.9 pp) |
| Unusable answers | 5 | **1** | | |

Per class (all rows): `OTHER` recall 0.60 -> 0.66, F1 0.72 -> 0.77; `PRODUCT` predicted 49 -> 47 for 14 true (precision
0.22 -> 0.26), 35 `OTHER` rows still answered `PRODUCT` (v1: 38); relevance `HIGH` predicted 31 -> 34 for 18 true;
sentiment recall `NEGATIVE` 0.74 -> 0.79, `POSITIVE` 0.61 -> 0.63. Random-row sentiment: accuracy unchanged (0.907);
the F1 drop is one `POSITIVE` row missed and two more `NEUTRAL` rows answered `POSITIVE`, on classes of 9 and 15 rows
(the noise ADR 0018 predicted). The remaining unusable answer is an item listed under `KO` that is about Coca-Cola
Consolidated (`COKE`): the model named no ticker but answered `MEDIUM`, which the v2 rule rejects.

The gate passed: sentiment macro F1 0.809 vs 0.785 (+2.4 pp, limit -3 pp), message "prompt v2 vs baseline v1".

## Options

1. **Adopt the v2 run as `baseline.json`.** It is the prompt in production; it is better on the gated metric, on
   unusable answers and on event type; the gate then compares the next change against 0.809.
2. Keep the v1 baseline (0.785). A looser gate for the next change, measured against a prompt that no longer runs.

## Decision

Option 1 (owner's choice, 2026-11-14). `evals/results/news-classification/baseline.json` is the v2 run. Prompt `v2`
stays in production on Haiku 5.5.

## Consequences

- The next prompt or model change must keep sentiment macro F1 >= 0.779 (0.809 - 3 pp) on this golden set.
- The empty-ticker rule did what ADR 0023 expected (unusable 1.9% -> 0.4%). The `PRODUCT` definition helped little:
  `PRODUCT` is still predicted more than three times as often as it is true. Relevance `HIGH` over-prediction grew a
  little. Both stay open for a later prompt version, worded from the label definitions and synthetic examples only.
- The item-listed-under-a-related-ticker case (`KO` vs `COKE`) is a data question: the item is fetched for `KO`, the
  model says it is about another company. A `MEDIUM` answer without a ticker stays invalid; revisit if it shows up
  live.
- Random-row metrics on the small sentiment classes swing by about 5 pp per one or two rows; read them next to the
  confusion matrix, not as a gate.
