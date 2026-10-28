# News classification eval results

Written by the `news-classification-eval` job ([ADR 0018](../../../docs/decisions/0018-classification-eval.md)).
Metrics and counts only: the golden set's text is Finnhub data and is never written here.

| File | What | Committed |
|---|---|---|
| `latest.json` | Every metric of the last run (all rows and `random` rows; per class; confusion matrices; cost) | When it becomes the reference, as `baseline.json` |
| `latest.md` | The same as the PR comment | Optional, next to the baseline |
| `baseline.json` | The reference run the gate compares against (sentiment macro F1, max 3 pp drop) | Yes |

## Run locally (paid, ~0.02 USD on Haiku 5.5 at batch price)

From the repo root, Git Bash, with Postgres up (`docker compose up -d postgres`):

```bash
export EVAL_DATA_PASSPHRASE=...   # same as the GitHub Secret
export ANTHROPIC_API_KEY=...
bash evals/scripts/decrypt-golden.sh
(cd app && ./mvnw -q -DskipTests package)
SPRING_PROFILES_ACTIVE=job,eval java -jar app/target/briefwise-app-*.jar --briefwise.job=news-classification-eval
```

Run from the repo root: the golden set and results paths are relative to it. Exit code 0 = passed, 1 = regressed,
refused (budget, few-shot leakage) or the batch did not end within 60 min (the batch id is in the log).

## Make a run the baseline

Only on purpose (new prompt version accepted, golden set changed): copy `latest.json` to `baseline.json`, commit
both with the prompt change, and say why in the commit message. CI never overwrites the baseline. Changes under
`evals/results/` do not trigger `evals.yml`.
