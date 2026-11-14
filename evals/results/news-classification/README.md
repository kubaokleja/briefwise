# News classification eval results

Written by the `news-classification-eval` job ([ADR 0018](../../../docs/decisions/0018-classification-eval.md)).
Metrics and counts only: the golden set's text is Finnhub data and is never written here.

| File | What | Committed |
|---|---|---|
| `latest.json` | Every metric of the last run (all rows and `random` rows; per class; confusion matrices; cost) | When it becomes the reference, as `baseline.json` |
| `latest.md` | The same as the PR comment | Optional, next to the baseline |
| `baseline.json` | The reference run the gate compares against (sentiment macro F1, max 3 pp drop; only on the same golden set, `cases` + `goldenSha256`) | Yes |

## Run locally (paid, ~0.02 USD on Haiku 5.5 at batch price)

From the repo root, Git Bash, with Postgres up (`docker compose up -d postgres`):

```bash
export EVAL_DATA_PASSPHRASE=...   # same as the GitHub Secret
export ANTHROPIC_API_KEY=...
bash evals/scripts/decrypt-golden.sh
(cd app && ./mvnw -q -DskipTests package)
SPRING_PROFILES_ACTIVE=job,eval java -jar app/target/briefwise-app-*.jar --briefwise.job=news-classification-eval
```

Run from the repo root: the golden set and results paths are relative to it. Exit code 0 = passed (or a new baseline
accepted), 1 = regressed, not comparable, refused (budget, few-shot leakage) or the batch did not end within 60 min
(the batch id is in the log).

## Make a run the baseline

Only on purpose (new prompt version accepted, golden set changed): copy `latest.json` to `baseline.json`, commit
both with the prompt change, and say why in the commit message. CI never overwrites the baseline. Changes under
`evals/results/` do not trigger `evals.yml`.

## Comparable runs ([ADR 0022](../../../docs/decisions/0022-eval-baseline-comparability.md))

A run is gated only against a baseline on the same golden set: same `cases` and same `goldenSha256` (SHA-256 over the
parsed clear-text cases, logged at the start of every run; see `GoldenSetFingerprint`). A different model or prompt
version is compared as usual and named in the gate message.

If the golden set changed (rows added or relabelled with the `new-eval-case` skill), the run fails as
`NOT COMPARABLE`. When the change is intended, rerun with the new baseline accepted, then commit its `latest.json` as
`baseline.json`:

- GitHub: Actions -> Evals -> Run workflow, tick `accept_new_baseline`.
- Locally: add `--briefwise.news.eval.accept-new-baseline=true` to the `java -jar` command above.

The flag only lets a not-comparable run pass (`BASELINE ACCEPTED`); on the same golden set the 3 pp gate still applies.
The 2026-11-07 baseline got its `goldenSha256` backfilled by hand (computed from the decrypted set, no model call).
