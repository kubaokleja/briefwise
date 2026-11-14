# 0022. Eval baseline comparability: only the same golden set is gated

- Status: Accepted
- Date: 2026-11-14

## Context

The classification eval ([ADR 0018](0018-classification-eval.md), [DESIGN 10](../DESIGN.md#10-evaluation-strategy)) gates a
run on sentiment macro F1 at most 3 pp below the committed `baseline.json`. The gate compared only that number:
`EvalBaseline` read `model` and `promptVersion` but nothing used them, and no run recorded which golden set it
measured. Once the golden set grows or is relabelled (`new-eval-case` skill), a run would be gated against a score
measured on other rows, and the pass/fail would mean nothing. Found in the M1 close-out (eval follow-up (2), moved to
M2 slice 1).

A relative gate needs a fixed ruler. The golden set is the ruler. The model and prompt are what gets measured: a
prompt-v2 PR *must* be compared with the v1 baseline, which is the whole point of the gate.

## Options

**What makes a baseline not comparable**
1. **Golden set only** (case count + content hash). A model or prompt difference is compared and named in the message.
   Keeps the gate useful for every prompt and model PR.
2. Golden set + model. A model swap would need an explicit accept; but comparing a new model with the old baseline is
   exactly the L2 question ([ADR 0020](0020-classification-model-from-first-eval.md)).
3. All four (model, promptVersion, cases, hash), the first PLANNING wording. Every prompt PR would need the accept
   flag, so the gate would never catch a worse prompt.

**Fingerprint**
1. **SHA-256 over the parsed cases** in the domain (`GoldenSetFingerprint`, JDK `MessageDigest`, no dependency):
   sorted by id; id, sample, sorted tickers, source, headline, summary, three labels; U+001F between fields, U+001E
   after each case; UTF-8; hex. Independent of line endings, BOM and column order; any edit of a case changes it.
2. SHA-256 of the decrypted file bytes. Simpler, but re-saving the CSV (CRLF vs LF) would break comparability with no
   case changed. The `.gpg` bytes cannot be used at all: they differ on every encryption.

**Existing baseline (2026-11-07, no hash)**
1. **Backfill once by hand**, then a baseline without a hash is not comparable.
2. Tolerate a missing hash (compare on count only, with a warning): weaker until the next baseline.

**Accept path**
1. **Property `briefwise.news.eval.accept-new-baseline`** (default false) + boolean `workflow_dispatch` input
   `accept_new_baseline` in `evals.yml`. The maintainer still copies `latest.json` to `baseline.json`.
2. The job writes `baseline.json` itself when the flag is set: less copying, but CI would write the reference, against
   the README rule.

## Decision

Option 1 in each group.

- `RegressionGate.check(run, baseline)` compares the run in baseline shape (`EvalReport.asBaseline()`). Verdicts:
  `NO_BASELINE` (pass), `PASSED`, `REGRESSED`, `NOT_COMPARABLE` (fail: cases differ, hash differs or the baseline has
  no hash), `BASELINE_ACCEPTED` (pass, only with the flag and only for a not-comparable run). The flag cannot hide a
  regression: on the same golden set the 3 pp gate always applies. A not-comparable message still shows the F1 delta,
  marked "not gated".
- `EvalRun.Status` and `NewsFacade.EvalStatusView` gain `NOT_COMPARABLE` and `BASELINE_ACCEPTED`; only `PASSED` and
  `BASELINE_ACCEPTED` succeed, so the job exits 1 on a not-comparable run.
- `latest.json` carries `goldenSha256`; the gate block carries `verdict`, `baselineCases`, `baselineGoldenSha256`.
  `latest.md` shows the verdict and the first 12 hash characters. The hash identifies the set without revealing text.
- The fingerprint is logged at the start of every run, before the batch is submitted.
- Backfill: `goldenSha256` `a80c98dd1486029c14456b511cb0c4ac0573305dec8e00113e747327feb82711` (258 cases) added to
  the 2026-11-07 baseline. Computed on 2026-11-14 with the production `CsvGoldenSet` + `GoldenSetFingerprint` on the
  local clear-text set, after checking it is byte-identical to a fresh decryption of the committed `.gpg`. No model
  call, no cost.
- `evals.yml`: a PR run passes `false` (no inputs on `pull_request`); only a manual dispatch can accept.

## Consequences

- A golden set change cannot silently pass or fail the gate; it needs a deliberate manual run and a baseline commit.
- Changing a golden set costs one extra eval run (~0.012 USD on Haiku 5.5, batch price) if the PR run fails first.
- The canonical form is a contract with every committed baseline. `GoldenSetFingerprintTest` pins a hash computed
  independently (Python) from the documented form; changing the form makes all baselines not comparable.
- Model and prompt differences are reported, not blocked: a reviewer reads "prompt v2 vs baseline v1" in the PR
  comment and decides whether the new run becomes the baseline.
- Revisit if the eval gains other inputs that change the ruler (for example a second golden set for the RAG eval in
  M2 slice 4, which will need its own fingerprint).
