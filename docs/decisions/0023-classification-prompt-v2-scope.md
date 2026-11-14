# 0023. Classification prompt v2 scope: empty tickers on LOW items, narrower PRODUCT

- Status: Accepted
- Date: 2026-11-14

## Context

The first classification eval ([ADR 0020](0020-classification-model-from-first-eval.md); Haiku 5.5, prompt v1,
258 golden rows, 2026-11-07) left two prompt follow-ups (decision P1, M2 slice 1):

1. **Empty `affectedTickers`.** 5/258 answers (1.9%) in the eval, 2.9% live (Neon backfill, 2026-11-07); all 5 eval
   rows are labelled `OTHER`. `ClassificationAnswer.toClassification` rejects an empty list, so each one costs a
   re-ask (a second paid call) and ends `UNCLASSIFIED`. Downstream, affected tickers are used only by
   `SelectionPolicy`, which keeps items with relevance >= `MEDIUM` (`briefwise.news.selection.min-relevance`) whose
   affected tickers include a held one; a `LOW` item without tickers would never be shown anyway.
2. **`PRODUCT` over-predicted.** Event type `PRODUCT`: 49 predicted, 14 true (precision 0.22, recall 0.79); 38
   `OTHER` rows were answered `PRODUCT`. The v1 definition ("products, services, partnerships, customer contracts")
   is broad. The labelling rule ([evals/labelling/README.md](../../evals/labelling/README.md)) keeps customer
   contracts and partnerships in `PRODUCT`.

The fix may not be tuned on the golden set: reading its errors to word the prompt would overfit the eval to these
258 rows (and the per-case answers of that run were not kept). Wording comes from the label definitions and new
synthetic examples only.

Structured outputs can enforce a non-empty list: the API supports `minItems` with values 0 and 1 only
([structured outputs docs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs), checked
2026-11-14).

## Options

**(1) Empty tickers**
1. **Allow an empty list only with relevance `LOW`.** The prompt allows `[]` when the item is not materially about
   any listed ticker. The code accepts `[]` only with `LOW`; otherwise it stays invalid (re-ask, then fallback). An
   honest answer instead of a forced guess, no second call, and the schema (and its cached grammar) stays the same.
2. Enforce at least one ticker in the schema (`minItems: 1`). Zero empty answers by construction, but the model must
   guess a ticker for an item it judged generic: a small, invisible hallucination. Also needs a check that Spring AI's
   schema generator carries the constraint, possibly through a new generator module (dependency).
3. Leave as is: ~3% of items pay a re-ask and end unclassified.

**(2) `PRODUCT` / `OTHER`**
1. **Narrower definition + one synthetic `OTHER` example that names a product.** Definition and example push the
   same way; about 100 more prompt tokens, inside the cached prefix.
2. Definition only: smaller change, weaker signal to the model.
3. Example only: smallest text change; the boundary stays unstated.

**Versions**
1. **One prompt `v2` with both changes**: one eval run (~0.012 USD at batch price), one replay re-recording. The
   effects land on different metrics (unusable answers vs event type F1), so they stay attributable.
2. `v2` and `v3` separately: cleaner attribution, two runs and two re-recordings.

## Decision

Option 1 in each group (owner's choice, 2026-11-14).

- `affectedTickers` in `system.md`: "the tickers the item is materially about; an empty list only when the item is
  not materially about any of them, and then relevance is LOW". `ClassificationAnswer.toClassification` accepts an
  empty list only when `relevance == LOW`. The JSON schema does not change. The existing multi-ticker listicle example
  (three tickers, `LOW`) stays as it is.
- `PRODUCT` in `system.md`: an event the company itself announces or is party to (launch, recall, pricing change,
  partnership, customer contract). Reviews, buying guides, how-tos and articles where a product is only mentioned are
  `OTHER`. One new synthetic `OTHER` example that names a product; the few-shot leakage guards (`build_golden.py`,
  batch adapter) must stay green.
- One prompt version `v2` (version bump + SHA-256 guard), replay fixtures re-recorded for it, one eval run. The run is
  compared with the v1 baseline per label; it becomes the new baseline only if the owner accepts it (same golden set,
  so the gate applies, [ADR 0022](0022-eval-baseline-comparability.md)).

## Consequences

- Generic `LOW` items get a classification instead of `UNCLASSIFIED`, and the eval counts them as valid answers: the
  unusable rate should fall towards 0 and `OTHER` recall should rise a little through (1) alone. Read the event type
  result with that in mind.
- A `MEDIUM`/`HIGH` item with no ticker is still rejected: relevance claims a company-specific effect, so it must name
  the company.
- An item with no affected ticker never reaches a portfolio's relevant list or summary; that is the intended meaning
  of "not about any held company".
- The sentiment gate (-3 pp) still guards the change: a v2 that helps event type but hurts sentiment fails.
- Revisit `OTHER` -> `MACRO` (10 rows) and `HIGH` relevance over-prediction in a later prompt version, again from
  definitions and synthetic examples only.
