# Hand labelling: news headlines

Input for the classification golden set (M1 slice 8) and the few-shot pool (slice 4). Label **before** any model
output exists for these items, so the model's answers cannot shape the labels.

## Export

1. Start the stack: `docker compose up -d postgres` (repo root).
2. Run one ingest with a long lookback (needs a free Finnhub key; the free tier has one year of history):

   ```powershell
   $env:FINNHUB_API_KEY = "<your key>"
   cd app
   ./mvnw spring-boot:run "-Dspring-boot.run.arguments=--briefwise.news.ingest.on-startup=true --briefwise.news.ingest.lookback-days=30"
   ```

   Look for `Startup news ingest: N new, ...` in the log, then stop the app.
3. Export (repo root):

   ```powershell
   Get-Content evals/labelling/export_headlines.sql | docker compose exec -T postgres psql -U briefwise -d briefwise --csv -q -f - | Select-Object -Skip 2 | Set-Content -Encoding utf8 evals/labelling/headlines_to_label.csv
   ```

   (`-Skip 2` drops the `setseed` result and an empty line; the first line must be the CSV header `split,id,...`.)
4. Append the stratified company sample. The random export is mostly passing mentions (Finnhub tags tickers
   loosely), so it has almost no HIGH-relevance or non-`OTHER` items. Headlines that name the held company fill
   those classes; already-labelled rows are skipped and never re-split:

   ```powershell
   Get-Content evals/labelling/export_company_headlines.sql | docker compose exec -T postgres psql -U briefwise -d briefwise --csv -q -f - | Select-Object -Skip 2 | Set-Content -Encoding utf8 evals/labelling/company_candidates.csv
   python evals/labelling/append_company_sample.py evals/labelling/company_candidates.csv evals/labelling/headlines_to_label.csv 16
   ```

   The `sample` column says where a row came from: `random` rows show the real distribution, `company` rows are
   oversampled. Report eval metrics per class and for `random` rows alone, never pretend the mix is representative.

## Rules

- Fill `sentiment`, `relevance`, `event_type`; mark `duplicate_of_id` when two rows report the same event (this
  checks the D1 threshold, [ADR 0009](../../docs/decisions/0009-news-dedup-and-etf-news.md)).
- `golden` rows are for the eval only, `fewshot` rows only for prompt examples. Never move a row between them.
- **Licence:** these are Finnhub headlines (personal use, no redistribution). The CSV files here are git-ignored
  until we decide how the golden set may be published (Domain Questions Log, 2026-10-21).

## Label definitions

Agreed 2026-10-23 for labelling; the classification prompt (M1 slice 4) must use the same definitions.

| Column | Values | Meaning |
|---|---|---|
| `sentiment` | `NEGATIVE`, `NEUTRAL`, `POSITIVE` | Likely effect on the ticker's price from a shareholder's view, not the article's tone |
| `relevance` | `LOW`, `MEDIUM`, `HIGH` | Relative to the single ticker: would an RM whose client holds it want to see it this morning? `HIGH` = company-specific and likely price-moving (earnings, downgrade, lawsuit, deal); `MEDIUM` = company-specific but minor; `LOW` = clickbait, generic market talk, passing mention |
| `event_type` | `EARNINGS`, `GUIDANCE`, `M_AND_A`, `REGULATORY`, `LITIGATION`, `MANAGEMENT`, `MACRO`, `PRODUCT`, `OTHER` | Main event; analyst rating changes are `OTHER` with a note (candidate for an `ANALYST` value) |
| `duplicate_of_id` | an earlier row's `id` | Same event as that row; empty otherwise |
| `notes` | free text | Doubts, the chosen ticker for multi-ticker rows |

- **Multi-ticker rows:** one label, from the view of the most affected ticker; name that ticker in `notes`.
- Label from headline + summary only (what the model sees); do not open the link.

## Provenance of the current labels

The 2026-10-23 exports (200 `random` + 144 `company` rows) were labelled by Claude (Opus 5.5) as a draft, using the definitions above, before
any classification prompt existed. Because the classifier under evaluation is also an LLM, these labels may share
its biases: eval scores against them measure agreement with Claude, not ground truth. Before slice 8 treats them as
the golden set, a human reviews them (at least a random sample of golden rows) and records the agreement rate here.

- 2026-10-23: maintainer spot-checked about 40 rows; no disagreements (agreement ~100% on that sample).
