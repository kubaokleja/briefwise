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

## Rules

- Fill `sentiment`, `relevance`, `event_type`; mark `duplicate_of_id` when two rows report the same event (this
  checks the D1 threshold, [ADR 0009](../../docs/decisions/0009-news-dedup-and-etf-news.md)).
- `golden` rows are for the eval only, `fewshot` rows only for prompt examples. Never move a row between them.
- **Licence:** these are Finnhub headlines (personal use, no redistribution). The CSV files here are git-ignored
  until we decide how the golden set may be published (Domain Questions Log, 2026-10-21).
