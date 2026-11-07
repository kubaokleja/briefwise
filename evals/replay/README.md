# Replay fixtures

Recorded model answers for the `replay` provider ([ADR 0015](../../docs/decisions/0015-replay-provider.md)). CI
(`test` profile) and the demo answer LLM calls from these files instead of calling a model: zero cost, same answer
every run. The app reads every `*.jsonl` file here at startup (`briefwise.llm.replay.fixtures-dir`, relative to
`app/`) and checks it before `ai_generation_log`.

One JSON object per line:

| Field | Used for |
|---|---|
| `key` | Lookup: SHA-256 of feature, model, prompt version and the normalised data block (L7) |
| `model`, `response`, `recorded_at` | The replayed answer |
| `feature`, `prompt_version`, `subject_id` | Humans only |

A key that appears twice, or a malformed line, fails startup on purpose. A prompt version bump changes every key, so
re-record after one: old lines just stop matching (delete them).

## Licence

Only synthetic input goes here. `synthetic_news.sql` holds hand-written items. Recordings of real Finnhub headlines
must not be committed (licence: no redistribution of data "or derived results"; PLANNING Domain Questions,
2026-10-21). `export_replay.sql` exports only rows for the synthetic item ids.

## Recording (one-off, manual, never in a test)

This costs real money: about 10 Haiku 5.5 calls, well under 0.01 USD (twice for the cache check). It needs
`ANTHROPIC_API_KEY`. It also captures the raw HTTP responses that replace the hand-written WireMock fixtures in
`app/src/test/resources/anthropic/`.

1. Start the stack and create a throwaway database, so only synthetic news exists while the model is live (the
   development database may hold real Finnhub items still `RECEIVED`, which the sweep would send to the model):
   ```powershell
   docker compose up -d postgres kafka
   docker compose exec -T postgres psql -U briefwise -d briefwise -c "DROP DATABASE IF EXISTS briefwise_recording" -c "CREATE DATABASE briefwise_recording"
   ```
2. Start a recording proxy in front of the Claude API. WireMock standalone is already in the local Maven repository.
   Use a directory outside the repo. On this machine the JVM needs the Windows trust store (Avast TLS scanning):
   `JAVA_TOOL_OPTIONS` from the user environment.
   ```powershell
   $env:JAVA_TOOL_OPTIONS = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS','User')
   java -jar $HOME\.m2\repository\org\wiremock\wiremock-standalone\3.13.2\wiremock-standalone-3.13.2.jar `
     --port 8089 --proxy-all https://api.anthropic.com --record-mappings --root-dir $env:TEMP\briefwise-recording
   ```
3. Start the app on the `local` profile with Anthropic behind the proxy, replay on and `on-miss: fallback`. Every miss
   calls the model once and records a replayable row:
   ```powershell
   $env:ANTHROPIC_API_KEY = '<your key>'   # this shell only (or from the git-ignored .env); never in a tracked file
   $env:DB_URL = 'jdbc:postgresql://localhost:5432/briefwise_recording'
   cd app; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.ai.model.chat=anthropic --spring.ai.anthropic.base-url=http://localhost:8089 --briefwise.llm.provider=replay --briefwise.llm.replay.on-miss=fallback"
   ```
4. Seed the items: `docker compose exec -T postgres psql -U briefwise -d briefwise_recording < evals/replay/synthetic_news.sql`.
   The outbox relay sends them to `news.raw` and the consumer classifies them. Check:
   `SELECT status, count(*) FROM news_items WHERE source = 'Synthetic' GROUP BY 1;`
5. Cache check (prompt caching, ADR 0012): the seeded items are classified within seconds of each other, so the
   first call writes the system prompt to the cache and the rest read it. No extra calls needed:
   `SELECT cache_write_tokens, cache_read_tokens FROM ai_generation_log WHERE feature = 'news-classification' ORDER BY created_at;`
   shows one write and non-zero reads (2026-11-07: 2,058 written once, then read by the other 9 calls).
6. Export:
   `docker compose exec -T postgres psql -U briefwise -d briefwise_recording -At -v feature=news-classification < evals/replay/export_replay.sql > evals/replay/news-classification.jsonl`
   Daily summaries (ADR 0017): the summary only selects items published in the last 24 h, so first run
   `UPDATE news_items SET published_at = now() - interval '1 hour' WHERE source = 'Synthetic';`. Then
   `POST /api/admin/jobs/news-ingest/run` (its last step summarises every portfolio; without a Finnhub key the
   ingest step fails but the summarize step still runs) and export with `-v feature=news-summary` to
   `evals/replay/news-summary.jsonl` (only summaries of seeded portfolios that cite synthetic items alone). A summary recording only replays while the selected items (ids, labels, text)
   are exactly the same, so it suits a seeded demo, not CI with random ids.
7. Stop the proxy. From `briefwise-recording/__files/`, copy one valid classification response body to
   `app/src/test/resources/anthropic/classification-recorded.json` (replace it; keep the hand-written
   `classification-valid.json`, which covers edge cases a live call does not produce). Update the token counts and cost
   in `LlmNewsClassifierIT` to match. Only the body is used: the mapping files hold response headers (`request-id`,
   organisation) and stay outside the repo; check the body contains no `x-api-key`, `anthropic-organization-id` or
   `request-id`.
8. `./mvnw verify` in `app/`. Drop the throwaway database afterwards (`DROP DATABASE briefwise_recording`).
