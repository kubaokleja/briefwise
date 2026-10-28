---
name: new-eval-case
description: Use when adding or correcting labelled cases in a Briefwise eval golden set (e.g. "add NEGATIVE examples to the classification eval", "fix the label of row X", "grow the golden set"). Covers labelling before model output, the golden / few-shot split, the leakage checks, the encrypted golden file and when to re-baseline. Pattern first implemented by hand for the news classification eval (ADR 0010, ADR 0018).
---

# New eval case

Reference: news classification golden set (`evals/labelling/`, `evals/sentiment_golden.csv.gpg`,
[ADR 0010](../../../docs/decisions/0010-golden-set-labelling.md), [ADR 0018](../../../docs/decisions/0018-classification-eval.md)).
Label definitions live in `evals/labelling/README.md`; do not restate them here.

## Rules

- **Label before the model answers.** Never look up what the classifier said for a row before labelling it; never
  copy a model answer into a label. If Claude drafts labels, record that in the provenance section of the README
  and have the maintainer spot-check them.
- **Golden and few-shot never mix.** A row's `split` is fixed once written. Prompt examples come only from the
  `fewshot` split or are synthetic; a golden headline must never appear in a prompt (`build_golden.py` and the
  batch adapter both refuse it).
- **No news text in git.** The labelling CSVs and `evals/sentiment_golden.csv` are git-ignored (Finnhub licence);
  only the `.gpg` copy and metrics are committed.
- **Keep the `sample` honest.** `random` rows come from the random export; oversampled rows are `company` (or a new
  value named in the README). Metrics on `random` rows alone must stay meaningful.

## Steps

1. Get the clear files: `bash evals/scripts/decrypt-golden.sh` (needs `EVAL_DATA_PASSPHRASE`); the source of truth
   for labels is `evals/labelling/headlines_to_label.csv` (local, git-ignored).
2. Add or fix rows there: new rows via the export / `append_company_sample.py` flow in the labelling README; fill
   `sentiment`, `relevance`, `event_type`, `notes`. Aim at thin classes (NEGATIVE, HIGH, rare event types).
3. Rebuild and check: `python evals/labelling/build_golden.py evals/labelling/headlines_to_label.csv
   prompts/news-classification/system.md evals/sentiment_golden.csv` (repairs mojibake, validates labels, leakage).
4. Encrypt: `bash evals/scripts/encrypt-golden.sh`; commit only `evals/sentiment_golden.csv.gpg` plus the README
   provenance line (date, how many rows, who labelled, agreement on a spot-check).
5. Re-baseline: the golden set changed, so the old baseline is not comparable. Run the eval
   (`evals/results/news-classification/README.md`), then copy `latest.json` to `baseline.json` in the same PR and
   say so in the commit message. Never re-baseline to make a regressed prompt pass.
6. If the class counts moved a lot, check whether the 3 pp gate still fits the noise (ADR 0018 consequences).

## Another eval (RAG, brief)

Same shape: golden data outside the prompt pool, labelled first, encrypted if the source text is licensed, metrics
only in `evals/results/<eval>/`, a baseline file and a relative gate. Ask before adding a new dataset format.
