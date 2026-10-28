# 0018. Classification eval: metrics, harness, golden-set publication and gate

- Status: Accepted
- Date: 2026-10-28

## Context

M1 slice 8 adds the first LLM eval: news classification against the hand-labelled golden set
([DESIGN 10](../DESIGN.md#10-evaluation-strategy), [ADR 0010](0010-golden-set-labelling.md)). DESIGN asks for macro F1,
a confusion matrix and cost per 1,000 items, LLM evals only on `workflow_dispatch` or `prompts/**` / `evals/**`
changes, through the Message Batches API, with a merge gate of "F1 drops by no more than 3 pp". Open points from
PLANNING: E1 (metric scope), E2 (harness), and the Domain Questions Log entry of 2026-10-21 (may the golden set,
real Finnhub headlines, be published?). The repo is public and Finnhub's licence forbids redistributing its data
"or derived results".

The golden set: 258 rows (150 `random`, 108 oversampled `company`), labels drafted by Claude before any model output
and spot-checked by the maintainer (~40 rows 2026-10-23; all 14 golden `ANALYST` relabels 2026-10-28). Strongly
imbalanced: sentiment NEUTRAL 201 / POSITIVE 38 / NEGATIVE 19; relevance LOW 182 / MEDIUM 58 / HIGH 18; event type
OTHER 181, five classes with fewer than 5 rows, `MANAGEMENT` none.

## Options

**E1: metric scope**
1. Sentiment only, as DESIGN 10 reads. Simplest; relevance (which drives the summary selection) goes unmeasured.
2. All three labels reported, only sentiment macro F1 gated. Full picture; the gate stays on the label with enough
   rows per class to be stable.
3. All three gated. Strictest, but event-type classes of 2-4 rows make F1 jump by tens of points per case: false
   CI failures.

**E2: harness**
1. Java runner in `app/`, reusing `NewsClassificationPrompt.render()`, the `ClassificationAnswer` schema and the
   prompt hash guard; batches through the Anthropic Java SDK (already on the classpath via Spring AI). The eval
   measures exactly the shipped prompt. Metrics are hand-written (~100 lines, unit-tested).
2. Python + uv + `anthropic` (+ scikit-learn) in `evals/`. Shortest code; duplicates prompt rendering and schema, so
   needs a drift test; 1-2 new dependencies.
3. promptfoo. Good matrices and reports; Node toolchain, duplicated prompt, no Batch API (2x the price).

**Golden set in CI** (the text may not be published)
1. Commit only a gpg-encrypted copy (AES-256, symmetric), passphrase in a GitHub Secret; decrypt at run time.
   GitHub's documented pattern for secrets over the 48 KB secret limit (the CSV is ~130 KB).
2. Keep it local, commit only results; `evals.yml` manual-only. No automatic gate on prompt changes.
3. Synthetic paraphrases committed in clear. Public, but labels may no longer fit, and a paraphrase may still be a
   "derived result".

**Eval matrix for the first run**: Haiku 5.5 as is; + Sonnet 5.5 (~3x cost per 1,000); + Haiku without few-shot
(ablation); + Alpha Vantage vendor sentiment as an external baseline (new key and adapter).

## Decision

- **E1: option 2.** Macro F1, accuracy, per-class precision / recall / F1 / support and a confusion matrix (with an
  `invalid` column) for sentiment, relevance and event type, each on all golden rows and on `random` rows alone.
  Macro F1 averages over every class present in the truth or the predictions (a never-predicted class scores 0, a
  class predicted but absent counts as a false alarm). Classes with fewer than 10 cases are marked "too few".
  Unusable answers (malformed, errored, expired) count as misses, never as skipped cases. No re-ask: the eval
  measures first-attempt quality.
- **Gate:** only sentiment macro F1 on all rows, at most 3.0 pp below `evals/results/news-classification/baseline.json`
  (`briefwise.news.eval.max-drop-points`). No baseline: the run passes and says so. The baseline is a committed copy
  of a chosen run's `latest.json`; CI never overwrites it.
- **E2: option 1.** `AnthropicBatchClassifier` in `news.infrastructure.ai`: one Message Batch per run, one request per
  case (`custom_id` = case id), the production system prompt with a cache marker, the rendered `<news_item>` block,
  the production model, `max-tokens` and effort, and the `OutputConfig` built by Spring AI's `AnthropicChatOptions`
  from the same `BeanOutputConverter` schema (identical request shape). Polls every 30 s, gives up after 60 min
  (`INCOMPLETE`, batch id logged). Answers go through `ClassificationAnswer.toClassification`.
- **Golden set: option 1.** `evals/sentiment_golden.csv` (built by `evals/labelling/build_golden.py`, git-ignored),
  committed only as `evals/sentiment_golden.csv.gpg`; `evals/scripts/{encrypt,decrypt}-golden.sh`; secret
  `EVAL_DATA_PASSPHRASE`. Results (`latest.json`, `latest.md`) hold metrics and counts only, never news text.
  Resolves the Domain Questions Log entry of 2026-10-21.
- **Matrix:** Haiku 5.5, prompt as is, only. The others go to the backlog; the L2 ADR records absolute numbers.
- **Cost control:** own feature `news-classification-eval` with 0.50 USD/day ([DESIGN 13](../DESIGN.md#13-cost-control):
  < 0.5 USD per run). Before submitting, a worst-case estimate (no cache hits, every answer at `max-tokens`,
  3 characters per token) must fit the remaining budget (258 rows: ~0.085 USD; expected real cost ~0.02 USD).
  `PricingTier.BATCH` (shared kernel) halves every token price in `ModelPrice`; the generation log and daily usage
  record batch calls at that price. Every result is logged (`subject_id` = case id, latency 0, not replayable).
- **Leakage guard:** `build_golden.py` and the adapter both refuse a golden headline that appears in the few-shot
  block of `system.md`.
- **Trigger:** job `news-classification-eval` (`--briefwise.job=...`, profiles `job,eval`, run from the repo root).
  Only `PASSED` succeeds: a regression, a refused run (budget, leakage, no Anthropic model) or an unfinished batch
  fail the step, so the job runner exits 1. `evals.yml`: `workflow_dispatch` + PRs touching `prompts/**` / `evals/**`
  (not `evals/results/**`), throwaway Postgres service, posts `latest.md` as one PR comment (`gh pr comment
  --edit-last --create-if-none`), uploads the results; skips with a notice when secrets are missing (forks).
- **Dependency:** none added. `anthropic-java-core` stays transitive through `spring-ai-anthropic`. Declaring it
  directly (planned) made Maven's nearest-wins mediation pick the SDK's victools 4.38.0 over Spring AI's 5.0.0 and
  broke Spring AI's schema generator (`NoClassDefFoundError` in `ClassificationAnswerTest`, `SummaryAnswerTest`).

## Consequences

- The eval measures the shipped prompt by construction; a prompt or schema change cannot drift away from it.
- Labels drafted by Claude measure agreement with Claude-like judgement, not ground truth; the maintainer
  spot-checks reduce but do not remove this bias. Report it next to every number.
- 258 rows with 19 NEGATIVE cases: one case moves NEGATIVE recall by ~5 pp and sentiment macro F1 by ~1-2 pp, so
  run-to-run noise is close to the 3 pp gate. Revisit the threshold (or grow NEGATIVE / POSITIVE rows with the
  `new-eval-case` skill) after a few runs.
- The gate is only as good as the secrets: without `EVAL_DATA_PASSPHRASE` and `ANTHROPIC_API_KEY` the workflow
  skips (green). Making the check required in branch protection is a manual step.
- The local admin endpoint can start a paid eval run (`POST /api/admin/jobs/news-classification-eval/run`); it is
  open until M4 like the other jobs (ADR 0016), capped by the 0.50 USD/day budget.
- When Spring AI is upgraded, the SDK version moves with it; the batch adapter's WireMock IT catches API changes.
- Follow-ups: first live run and baseline (maintainer), L2 ADR from its numbers, `new-eval-case` skill; backlog:
  Sonnet comparison, few-shot ablation, Alpha Vantage baseline.
