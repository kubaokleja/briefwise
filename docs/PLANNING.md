# Planning

Task tracker. Source of truth for scope: [DESIGN.md](DESIGN.md). Tick boxes at the end of each session
(`session-wrap-up` skill). AI decision points are listed in [DESIGN 8](DESIGN.md#8-ai-and-engineering-decision-points).

## M0 (F1) — Skeleton

Goal: bounded contexts, synthetic portfolios, market data, CI from the first commit
([DESIGN 5](DESIGN.md#5-features-and-milestones)).

### Documentation and Claude Code setup
- [x] `CLAUDE.md`
- [x] `docs/PLANNING.md`, `docs/PROGRESS.md`
- [x] ADR 0001 record-architecture-decisions
- [x] ADR 0002 execution-modes-and-cost-control
- [x] ADR 0003 runtime-github-actions-and-neon
- [x] Skills: `new-bounded-context`, `flyway-migration`, `adr`, `session-wrap-up`
- [x] `.gitignore`, `README.md` stub

### Project bootstrap
- [x] Move IntelliJ skeleton (`pom.xml`, `src/`) into `app/`; rename `org.example` -> `com.briefwise`; Maven wrapper
- [x] Verify current GA versions (Java 25, Spring Boot 4, Spring AI 2.0.x); confirm each dependency with me (Spring AI + Kafka deps deferred to M1)
- [x] Decide resilience library (Resilience4j vs `@Retryable`) -> [ADR 0005](decisions/0005-resilience-spring-retryable.md)
- [x] `application.yml` with `briefwise.*` properties ([13.2](DESIGN.md#132-execution-modes-feature-toggles)); `BriefwiseProperties`
- [x] Profiles `local`, `cloud`, `job`, `demo`, `test` (default + `test` exercised by tests; `cloud`/`job`/`demo` not yet run)
- [x] Structured logging (human-readable local, JSON in `cloud`/`job`) + `X-Correlation-ID` filter and MDC (filter + MDC tested; `JsonLoggingIT` checks the JSON line incl. `correlationId` under the `job` profile)

### Architecture
- [x] Package skeleton for the 12 contexts ([6.2](DESIGN.md#62-bounded-contexts)): facade-only skeletons for news, research, risk, brief, compliance, aiops, access (layers added with first content, option A); `jobs` and `mcp` have no facade and appear with their first adapter (they are already in the ArchUnit list)
- [x] `shared` kernel: `Ticker`, `Money`, `Citation` (+ `SourceType`), `TokenCount`
- [x] `ArchitectureTest` (domain purity, facade-only access, package-private services, public only facades/ports); each rule proven to fail

### Data
- [x] Flyway baseline; `ddl-auto: validate`
- [x] `portfolio`: clients, portfolios, positions; 3-5 synthetic portfolios seeded (3 seeded)
- [x] `market`: instruments, prices, returns; 30 US companies + 6 ETFs (V4+V6); price source [ADR 0004](decisions/0004-market-price-source-yfinance-snapshot.md): `quant/` refresh script, snapshot `data/prices/` (2021-10-18..2026-10-16), Java CSV importer (`MarketFacade.importPriceSnapshot`, on startup in `local`/`demo`)
- [x] Facades: `PortfolioFacade` (`getPortfolio`, `listPortfolios`, `getHoldings`), `MarketFacade` (`getPriceHistory`, `getDailyMove`)
- [x] Facade `@SpringBootTest` + Testcontainers tests (`*IT`); `docs/api.http` for portfolio and market

### Infrastructure and CI
- [x] `docker-compose.yml`: Postgres + pgvector, Kafka (KRaft), Ollama; `demo` profile (stack started 2026-10-18: Postgres healthy, Kafka broker started, Ollama 0.40.5 answers; app in `local` profile starts on it, health UP, snapshot loaded 45,180 rows)
- [x] `.github/workflows/ci.yml`: build, unit tests, ArchUnit, Testcontainers (Postgres + Kafka) (`app-verify`, `quant-verify`, `ui-verify` green on GitHub, PR #2; Kafka IT comes with M1)
- [x] Dependabot (Maven, GitHub Actions, uv done; npm entry added with `ui/`)
- [x] Python `quant/` with uv + ruff + pytest and a `quant-verify` CI job (7 offline tests; green on GitHub)
- [x] UI delivery strategy -> [ADR 0007](decisions/0007-ui-delivery-strategy.md)
- [x] `server.address=127.0.0.1` in `local` (admin endpoint is open until M4 auth)

### UI skeleton ([ADR 0007](decisions/0007-ui-delivery-strategy.md))
- [x] `ui/`: Vite + React + TypeScript 5.9.3, Node 24 in `.nvmrc`, Vite proxy `/api` + `/actuator` -> :8080
- [x] Design tokens from [DESIGN 15](DESIGN.md#15-ui-design) (Tailwind `@theme`, dark default), self-hosted Inter / IBM Plex Mono, tabular figures
- [x] API client: `X-Correlation-ID` per request, `ProblemDetail` -> `ApiError` with correlation ID, `useApi` hook
- [x] Components: `AppShell`, `Amount` / `Percent`, `Loading` / `Empty` / `ErrorPanel`
- [x] Status page: health + portfolio list
- [x] OpenAPI: springdoc 3.1.1 (`local`/`test` only), `OpenApiSpecIT` vs committed `docs/openapi.json`, openapi-typescript -> `ui/src/api/schema.d.ts`
- [x] ESLint (typescript-eslint, react-hooks, ban `dangerouslySetInnerHTML`); Vitest tests for API client and formatters
- [x] CI `ui-verify` (npm ci, lint, typecheck, API types up to date, test, build); Dependabot `npm` for `/ui` (grouped) (green on GitHub)

## M1 (F2) — News pipeline

Goal: ingest news via outbox + Kafka, deduplicate, classify with an LLM (structured output, generation log, budget,
replay), daily portfolio summary, daily scheduled pipeline, classification eval ([DESIGN 5](DESIGN.md#5-features-and-milestones),
[7.1](DESIGN.md#71-news-pipeline-m1)). Branch `feat/m1-news-pipeline`. One slice per session, in this order; each ends
with `./mvnw verify` green. Decision points are settled at the start of the slice that first needs them (ADR via `adr`).

### Slice 1 — aiops foundation (no   LLM)
- [x] Decide B1 budget unit, gate shape, day boundary -> [ADR 0008](decisions/0008-llm-daily-budget-gate.md) (USD per feature per UTC day, pre-check + post-record, fail closed)
- [x] Flyway: `ai_generation_log` (prompt, response, model, `prompt_version`, tokens, cost, latency, correlation ID) (V7), daily usage per feature (V8 `ai_daily_usage`)
- [x] Model pricing in `application.yml` (`briefwise.aiops.pricing`, Haiku 5.5 + Sonnet 5.5); cost per call (`ModelPrice`)
- [x] `AiopsFacade.recordGeneration`, `ensureBudgetAvailable` (added, ADR 0008), `checkAndRecordUsage` (daily budget per feature, B1), `getCostSummary`
- [x] `UsageLimitExceededException` -> HTTP 429 ProblemDetail; job step marked `SKIPPED` (slice 6)
- [x] Unit tests + facade `*IT`; no aiops endpoint until the M6 Admin UI, so no `api.http`

### Slice 2 — News domain, Finnhub ingest, dedup
- [x] Decide D1 dedup threshold + window -> [ADR 0009](decisions/0009-news-dedup-and-etf-news.md) (0.6 within 48 h, same ticker; near-duplicates stored `SKIPPED`; ETFs skipped for now)
- [x] Check current Finnhub free-tier limits and licence (~60 calls/min, 30/s cap, NA companies, 1 year history; personal use, no redistribution); `FINNHUB_API_KEY` in `.env.example` (GitHub Secret: slice 6, with the workflow)
- [x] Domain: `NewsItem`, `NewsStatus`, `NewsClassification`, `Sentiment`, `Relevance`, `EventType` ([6.3](DESIGN.md#63-domain-model-sketch)); plus `FetchedNews`, `UrlHash`, `DedupPolicy`, `IngestPolicy`, `HeldInstrument`, `IngestSummary`
- [x] Flyway V9: `news_items` (URL hash unique) + `news_item_tickers`; no trigram index on headline (not used by the dedup query, ADR 0009)
- [x] Finnhub adapter (`RestClient`, key in header, `RetryTemplate` on 429/5xx/timeout per ADR 0005) for held companies only; WireMock tests (wiremock-standalone 3.13.2)
- [x] Dedup: URL hash + pg_trgm headline similarity (D1); `NewsRepositoryAdapterIT` pins the example pairs
- [x] `NewsFacade.ingest` (+ `MarketFacade.getInstrument` for the ETF check); facade `*IT`
- [x] Export headlines for labelling (golden set + separate few-shot pool), labelled before any model output: 344 rows (200 random + 144 stratified company sample; 258 golden / 86 few-shot), drafted by Claude and spot-checked (provenance in `evals/labelling/README.md`)

### Slice 3 — Outbox and Kafka
- [x] Decide K1 outbox relay vs Debezium ([DESIGN 18](DESIGN.md#18-open-questions)), K2 Kafka key for multi-ticker news, K3 idempotency store (processed-messages table vs status check) -> [ADR 0011](decisions/0011-news-outbox-relay-key-and-idempotency.md) (polling relay; one message per item keyed by first-fetched ticker, ids only; status check + conditional write)
- [x] Dependencies: `spring-boot-starter-kafka` (Spring Kafka 4.1.1, kafka-clients 4.2.1) and `testcontainers-kafka` 2.0.5, both Boot-managed
- [x] Flyway V10 `news_outbox` (context-prefixed name); news item + outbox row in one transaction, only for `RECEIVED` items
- [x] Polling relay -> `news.raw` (key per K2, `SKIP LOCKED`, mark after ack, stops at first failed send); `NewsFacade.publishPending`, scheduler behind `briefwise.news.outbox.relay-enabled`
- [x] Idempotent consumer by `newsId` (K3, `NewsFacade.processIngested`); 3 attempts 1 s apart -> `news.raw.DLT`, malformed / unknown item straight to the DLT; placeholder `NewsClassifier` until slice 4
- [x] Testcontainers Kafka IT: happy path, duplicate delivery, retries -> DLT, malformed and unknown -> DLT (`NewsKafkaIT`); outbox adapter IT (`SKIP LOCKED`)
- [x] Skill `kafka-consumer` (pattern now implemented once)

### Slice 4 — Classification: first LLM adapter by hand
- [x] Decide L1–L6, R1 -> [ADR 0012](decisions/0012-news-classification-llm-call.md) (native structured output, Haiku 5.5 / `llama3.1:8b`, 4 synthetic few-shot in the system prompt, effort `low` + `maxTokens` 1024, `SYSTEM_ONLY` 5 min cache, no circuit breaker), [ADR 0013](decisions/0013-untrusted-content-screening-m1.md) (pattern list), [ADR 0014](decisions/0014-classification-output-shape.md) (relevance per holder, per item, `ANALYST`, `subject_id`)
- [x] Dependencies: Spring AI 2.0.2 BOM, Anthropic + Ollama starters; model names in `application.yml` only; `spring.ai.model.chat` per profile
- [x] Prompt in `prompts/news-classification/` (`prompt.properties`: version + SHA-256 guard); stable prefix first, news in a `<news_item>` block
- [x] `ComplianceFacade.screenUntrustedContent` (M1 pattern list, `InjectionSuspected` event)
- [x] Code gate: still `RECEIVED`, a ticker still held (else `SKIPPED`), budget per attempt
- [x] Classification -> `NewsClassification`; invalid structure -> one re-ask -> `UNCLASSIFIED` (reason); generation log (cache tokens, `subject_id`) + `checkAndRecordUsage`; `NewsClassified` event; Flyway V11, V12
- [x] Kafka consumer keeps calling `NewsFacade.processIngested` (now classifies); `classifyPending` moved to slice 6 (with the sweep)
- [x] WireMock tests with hand-written responses: valid, malformed, re-ask, unknown ticker, timeout, 529, budget (`LlmNewsClassifierIT`)
- [x] Relabel analyst rows `OTHER` -> `ANALYST` in the local CSV (22 rows)

### Slice 5 — Replay provider
- [x] Decide L7 normalisation of the replay key -> [ADR 0015](decisions/0015-replay-provider.md) (SHA-256 of feature, model, prompt version, data block; NFC, line endings, whitespace; case kept; hits not logged)
- [x] `ChatClient` advisor instead of a `ChatModel` decorator (ADR 0015); `AiopsFacade.findReplay`, `replay`, `isReplayed`; `on-miss: fail | fallback` (`ReplayMissException` -> item stays `RECEIVED`); Flyway V13 `replay_key`
- [x] `test` and `demo` use `replay` + `fail` (demo: dummy key, discard port); fixture store for `evals/replay/*.jsonl`; `synthetic_news.sql`, `export_replay.sql`, recording procedure in `evals/replay/README.md`
- [x] Recording run (2026-11-07, 0.0020 USD, throwaway DB `briefwise_recording`): `evals/replay/news-classification.jsonl`
  (10) + `news-summary.jsonl` (3); recorded body added as `classification-recorded.json` (+1 IT) beside the
  hand-written edge-case fixture; cache: 2,058 tokens written once, read by the other 9 calls. Fixed on the way:
  `fixtures-dir` failed `local` startup (bound as `String` now), export missed summaries (portfolio subjects)
- [x] Pricing escape after a billed call (found 2026-11-07): adapters priced the echoed model name, so a dated ID
  threw `UnpricedModelException` after billing (up to ~6 paid calls per item) -> [ADR 0019](decisions/0019-price-by-configured-model-and-startup-check.md):
  price by the configured name, echoed ID in `ai_generation_log.response_model` (V16), `AiopsFacade.ensureConfigured`
  at adapter startup, recording failures logged not thrown (classifier, summarizer, batch). `ResourceNotFoundException`
  (pre-call, free) left to the M3 unknown-instrument question
- [x] With the recording run, compare `response_model` with `model`: Anthropic echoed `claude-haiku-5-5` (no dated ID)
- [x] Skill `llm-adapter` (pattern now implemented once)

### Slice 6 — Inbound adapters and daily pipeline
- [x] Decide sweep shape, PROVIDER_ERROR retry, done condition, timeout exit code, admin auth before M4 -> [ADR 0016](decisions/0016-jobs-sweep-and-job-runner.md) (sweep = `classifyPending`, min-age 10m, batch 200, cap 3 -> `RETRIES_EXHAUSTED`, stop at first deferral; PROVIDER_ERROR reopened within 48h; Kafka consumer lag via `Admin`; exit 0/1/2; endpoint open until M4)
- [x] `jobs` context: `JobsFacade.run`, `JobService` (steps ingest -> publish -> drain -> sweep), `POST /api/admin/jobs/{job}/run`; `docs/api.http` (happy path, 404 unknown job, 400 malformed id)
- [x] Scheduler only when `briefwise.execution.mode=scheduled`, guarded by `jobs.<name>.enabled`
- [x] Job runner (`--briefwise.job=news-ingest`): waits for consumer lag 0 or `briefwise.job-runner.max-duration` (15m); exit code via `ExitCodeGenerator` + `BriefwiseApplication`; relay scheduler off in `job`
- [x] Neon project (EU, Frankfurt, Postgres 16, direct endpoint: the advisory lock needs a session) + secrets
  `NEON_JDBC_URL`, `NEON_DB_USER`, `NEON_DB_PASSWORD`, `ANTHROPIC_API_KEY`, `FINNHUB_API_KEY` (2026-11-07). First local
  `cloud,job` `news-ingest` against Neon (backfill, `max-duration` 40m): 9 tickers, 825 inserted, 801 `CLASSIFIED`,
  24 `INVALID_OUTPUT` (2.9%), 3 summaries, 0.080 USD, 33 min (drain ~30 min, ~1.9 s per item, one consumer), exit 0
- [ ] Variable `PIPELINE_ENABLED=true` only after the first `workflow_dispatch` of `morning-pipeline` on `master`
  passed (you)
- [x] Load the price snapshot into Neon once (2026-11-07): one-off `cloud,job` run with
  `--briefwise.market.price-snapshot.import-on-startup=true` (45,180 rows, 2021-10-18..2026-10-16; Neon 18 MB)
- [x] Daily move shows its date: Portfolio and Dashboard print "as of <last close>" (2026-10-16 until M3 refreshes prices daily)
- [x] `.github/workflows/morning-pipeline.yml`: news ingest + classification, Kafka service container, gated by `PIPELINE_ENABLED` (first `workflow_dispatch` after merge to `master`)
- [x] Sweep of old `RECEIVED` items: Flyway V14 (`sweep_attempts`, `RETRIES_EXHAUSTED`, partial indexes); `NewsSweepService`; repository + Kafka ITs (DLT'd item swept)
- [x] Sweep and jobs follow-ups (found 2026-11-07) -> [ADR 0021](decisions/0021-job-run-limits-status-and-lock.md):
  provider-error window 96h; `sweep` step `FAILED` when at least half the picked items failed, `SKIPPED` on deferral,
  `TIMED_OUT` at the deadline; `max-duration` is the deadline of publish, drain, sweep and summaries (between items;
  ingest unbounded); `JobLock` (`PostgresJobLock`, advisory lock, `news-ingest` + `news-classify` share
  `news-pipeline`). DESIGN 7.1 wording fixed. Tests: `NewsSweepIT` (provider-error chain, multi-day budget deferral,
  deadline), `PostgresJobLockIT`, `JobServiceTest` (+8), `NewsSweepServiceTest` (+1), `PortfolioSummaryServiceTest` (+1)
- [ ] After the first Neon runs, check `gaveUp` / `failed` / `stoppedOnDeferral` / `stoppedOnDeadline` in the runner
  output (ADR 0016 / 0021 thresholds)
- [x] `NewsFacade.classifyPending`, `consumerLag`; stopgap `NewsStartupIngest` removed (labelling README uses the admin endpoint)

### Slice 7 — Daily portfolio summary
- [x] Decide S1 model, trigger, `checkText` scope, selection, gate, patterns, disclaimer, limits, few-shot ->
  [ADR 0017](decisions/0017-daily-portfolio-summary.md) (Haiku 5.5, feature `news-summary`, effort `low`, 2048 tokens,
  0.05 USD/day; `summarize` step after the sweep, GET only reads; deterministic advice-phrasing rules, one re-ask,
  template fallback; HIGH+MEDIUM, 24 h, by relevance then weight, cap 15; no few-shot)
- [x] `compliance`: `RuleId`, `Violation`, `ComplianceResult`, `Passage`, `TextRules`, `Disclaimer`;
  `TextCheckService` behind port `TextCheck`; `ComplianceFacade.checkText(TextCheckRequest)`, `disclaimer()`;
  `TextRulesTest` (true and false positives), `ComplianceFacadeIT` (+2)
- [x] Flyway V15: `news_portfolio_summaries` (portfolio + UTC date unique, points/violations JSONB, `is_fallback`,
  model, `prompt_version`, disclaimer; empty = no points), selection indexes; `FlywayBaselineIT` 14 -> 15
- [x] Selection: port `PortfolioHoldings` + `FacadePortfolioHoldings`; `NewsRepository.findClassified` (JDBC,
  affected tickers aggregated); `ClassifiedNews`, `RelevantNews`, `SelectionPolicy`; `RelevantNewsService`;
  `NewsFacade.getRelevantNews`; repository IT (affected flag, window, relevance, limit)
- [x] Summary: `PortfolioSummary`, `SummaryPoint`, `SummaryDraft`, `SummarizerResult`, `RegenerationGate`,
  `SummaryComposer` (citations, template); ports `NewsSummarizer`, `SummaryCompliance`, `SummaryRepository`,
  `PortfolioSummaries`, `RelevantNewsQuery`; `PortfolioSummaryService`; `NewsFacade.summarizeForPortfolio`,
  `summarizePortfolios`, `getLatestSummary` (`aiGenerated`, `fallback`, `empty`, disclaimer)
- [x] LLM adapter via the `llm-adapter` skill: `prompts/news-summary/` (v1 + hash guard), `SummaryAnswer`,
  `LlmNewsSummarizer`, `NoChatModelNewsSummarizer`, `NewsSummaryProperties`, `NewsSummaryConfig`;
  `briefwise.news.selection.*`, `briefwise.news.summary.*`, `daily-budget-usd.news-summary`
- [x] `jobs`: `summarize` step after the sweep (SKIPPED on timeout or deferral); `JobServiceTest` (+2),
  `JobsFacadeIT`
- [x] `GET /api/portfolios/{id}/news/relevant`, `/news/summary`; `docs/api.http` (200, 404, 400); OpenAPI + UI types
  regenerated
- [x] Tests: `SelectionPolicyTest`, `RegenerationGateTest`, `SummaryComposerTest`, `RelevantNewsServiceTest`,
  `PortfolioSummaryServiceTest`, `NewsSummaryPromptTest`, `SummaryAnswerTest`, `LlmNewsSummarizerIT` (WireMock),
  `ReplayNewsSummaryIT`, `SummaryRepositoryAdapterIT`, `NewsSummaryFacadeIT`, `PortfolioNewsControllerIT`;
  DESIGN 6.2, 6.3, 7.1, 13.7 updated
- [x] Recording run for `news-summary` (with the slice 5 run, 2026-11-07: 3 summaries, 0 violations, no fallback)
- [x] Summary follow-ups (1) and (2) (2026-11-07): a draft rejected by compliance that ends in the template is flagged
  in the generation log too (`Drafted.generationId`, port `GenerationAudit`, `AiopsFacade.markFallback`, which also
  clears the replay key); `generatedAt` is the selection time. Tests: `PortfolioSummaryServiceTest` (+5),
  `AiopsFacadeIT` (+1), `LlmNewsSummarizerIT`
- [x] Slice 10: `model` / `promptVersion` of the summary response are nullable, but the generated TS types say
  `string`; mark them nullable in the spec (or handle `null` in the UI)

### Slice 8 — Classification eval
- [x] Decide E1, E2, golden-set publication, matrix -> [ADR 0018](decisions/0018-classification-eval.md) (all three
  labels reported on all + random rows, only sentiment macro F1 gated at -3 pp vs `baseline.json`; Java batch runner
  reusing the production prompt; golden set committed only gpg-encrypted; first run Haiku 5.5 as is)
- [x] Human spot-check of the 22 `ANALYST` relabels (14 golden accepted, 14/14; `evals/labelling/README.md`)
- [x] `evals/labelling/build_golden.py` -> `evals/sentiment_golden.csv` (258 golden rows, mojibake repaired, label and
  few-shot leakage checks); `evals/scripts/{encrypt,decrypt}-golden.sh`; export command fixed for UTF-8
- [x] Encrypt and commit `evals/sentiment_golden.csv.gpg` (round trip checked; secret `EVAL_DATA_PASSPHRASE` set 2026-11-07)
- [x] Domain: `GoldenCase`, `EvalLabels`, `EvalPrediction`, `ConfusionMatrix`, `ClassMetrics`, `LabelScores`,
  `EvalScores`, `EvalReport`, `EvalBaseline`, `RegressionGate`, `BatchOutcome`, `EvalRun`; ports `GoldenSet`,
  `BatchClassifier`, `EvalReports`, `ClassificationEvals`; `ClassificationEvalService`; `NewsFacade.evaluateClassification`
- [x] `AnthropicBatchClassifier` (Message Batches via the SDK, same request shape as production, leakage + worst-case
  budget pre-flight, polling, per-result log and usage), `NoBatchClassifier`, `EvalBatchConfig`; `CsvGoldenSet`,
  `FileEvalReports` + `EvalMarkdown`; `PricingTier.BATCH` in aiops; budget `news-classification-eval` 0.50 USD/day
- [x] `jobs`: `news-classification-eval` (step `evaluate`, fails unless `PASSED`); `application-eval.yml`; `docs/api.http`
- [x] Tests: `LabelScoresTest`, `RegressionGateTest`, `EvalReportTest`, `ModelPriceTest` (+1),
  `ClassificationEvalServiceTest`, `CsvGoldenSetTest`, `FileEvalReportsTest`, `JobServiceTest` (+3);
  `AnthropicBatchClassifierIT` (WireMock), `NewsEvalFacadeIT`
- [x] `.github/workflows/evals.yml` (`workflow_dispatch` + `prompts/**`, `evals/**`; decrypt, run, PR comment, artifact)
- [x] First live run (2026-11-07, 0.0118 USD, throwaway DB): sentiment macro F1 0.785 (random 0.853), relevance
  0.676, event type 0.572, 5/258 unusable; committed as `baseline.json`
- [x] ADR: classification model choice from the eval (L2) -> [ADR 0020](decisions/0020-classification-model-from-first-eval.md) (Haiku 5.5 kept)
- [x] Skill `new-eval-case`
- [x] Eval follow-ups (1) and (3) (found 2026-11-07): a batch past `max-wait` is cancelled, then after
  `cancel-wait` (5m) its finished, billed requests are recorded in the generation log and `ai_daily_usage`; still
  `INCOMPLETE`, not scored (`AnthropicBatchClassifierIT` +1). `evals.yml` runs only on
  `prompts/news-classification/**`, `evals/sentiment_golden.csv.gpg`, `evals/scripts/**`; timeout 80 min

### Slice 9 — UI: News
- [x] Feed source: new `GET /api/news` (all visible statuses, optional portfolio filter) instead of the per-portfolio
  `relevant` list, which never contains `UNCLASSIFIED` items: `NewsFeedItem`, port `NewsFeed`, `NewsFeedService`,
  `NewsRepository.findFeed` (JDBC), `NewsFacade.getNewsFeed`, `NewsController`; `NewsFeedItemTest`,
  `NewsFeedServiceTest`, `NewsRepositoryAdapterIT` (+2), `NewsControllerIT`; `docs/api.http`
- [x] react-router 8.5.1 (ADR 0007 named 8.4.0; 8.5.1 current, declarative mode); router + nav in `AppShell`
- [x] `AiMarker` component (all ADR 0007 states) + Vitest
- [x] News feed: sentiment icon + label, relevance, event type, expandable rationale, `UNCLASSIFIED` (with reason),
  `RECEIVED`, headline + link only (http(s) only, `safeHref`); loading / empty / error; portfolio filter in the URL
- [x] "Run news now" button -> admin endpoint; step list, SKIPPED messages, 429 state
- [x] Budget state for "Run news now" (2026-11-07): shown when a step is `SKIPPED` with a budget message (the
  endpoint answers 200); the 429 branch is kept for a future endpoint. Admin runs skip `summarize` (no drain). Vitest
  +2, `JobServiceTest`

### Slice 10 — UI: Portfolio v0 and Dashboard v0
- [x] Portfolio daily move: `GET /api/portfolios/{id}/daily-move` (weight x daily change per priced position, summed;
  unpriced positions listed with the covered weight): `PortfolioDailyMove`, `InstrumentMove`, port `InstrumentMoves`
  + `FacadeInstrumentMoves` (market facade), `PortfolioFacade.getDailyMove`; `PortfolioDailyMoveTest`,
  `PortfolioQueryServiceTest` (+2), `PortfolioControllerIT` (+3)
- [x] Portfolio v0: positions, daily move, daily AI summary with AI marker, numbered sources, disclaimer, violations,
  template / empty / "no summary yet" states
- [x] Dashboard v0: portfolio tiles (daily performance, relevant news count)
- [x] Summary `model` / `promptVersion` nullable in the spec (`@Schema(types = {"string", "null"})`); OpenAPI spec +
  generated types updated; `ui-verify` green

## M2 (F3) — Research: cited Q&A over SEC filings and news

Goal: the RM asks "why is X down?" and gets a streamed answer built only from supplied passages (SEC filings, classified
news, the market move), every claim with a citation, "I don't know" when the sources do not support an answer
([DESIGN 5](DESIGN.md#5-features-and-milestones), [7.3](DESIGN.md#73-qa-with-citations-m2)). Concepts: embeddings,
pgvector + HNSW, cosine similarity, chunking, vector baseline -> hybrid, metadata filtering, query rewriting,
reranking, hallucination control, SSE streaming, RAG eval. Branch `feat/m2-research` from `master` once M1 is merged.
One slice per session, in this order; each ends with `./mvnw verify` green. Decision IDs below are new to M2 and are
settled at the start of the slice that lists them (ADR via `adr`). Filing ingest is on demand (local, Ollama
embeddings), not part of the morning pipeline ([7.3](DESIGN.md#73-qa-with-citations-m2)).

### Slice 1 — M1 carry-overs: eval comparability, classification prompt v2
- [ ] Eval follow-up (2): `EvalBaseline` gains `model`, `promptVersion`, `cases` (golden row count, golden file
  hash); `RegressionGate` refuses (or flags `NOT_COMPARABLE`) a baseline that differs, with an explicit
  `--accept-new-baseline` path; `RegressionGateTest`, `EvalReportTest`, `ClassificationEvalServiceTest` (+n)
- [ ] Decide P1 prompt v2 scope (ADR 0020 follow-ups): (1) empty `affectedTickers` on generic items (allow none for
  `OTHER`/`LOW`, or require at least one in the schema), (2) `PRODUCT` over-predicted vs `OTHER` (boundary wording,
  synthetic few-shot change; never from the golden set) -> ADR
- [ ] `prompts/news-classification/` v2 (version bump + SHA-256 guard), schema / `NewsClassification` mapping if (1)
  changes it; `NewsClassificationPromptTest`, `LlmNewsClassifierIT` (new fixture for the empty-ticker case)
- [ ] Re-record the classification replay fixtures for v2 (throwaway DB, `evals/replay/README.md` procedure)
- [ ] Eval run on v2 (Batch API, `evals.yml`): compare with v1 per label; new `baseline.json` only if accepted (you);
  result recorded in ADR 0020 or a follow-up ADR

### Slice 2 — Filing source and ETL (no embeddings)
- [ ] Decide G1 corpus: forms (10-K, 10-Q, 8-K items), how far back, all 30 companies or held ones, ETFs (no 10-K;
  skip like ADR 0009?), European / Swiss companies ([DESIGN 18](DESIGN.md#18-open-questions), "F3"); storage estimate
  against Neon 0.5 GB -> ADR
- [ ] Decide G2 source and snapshot: live EDGAR fetch vs a committed text snapshot (`data/filings/`, like ADR 0004;
  SEC filings are public domain, check), where ingest runs (local DB, Neon or both), ticker -> CIK mapping (`cik`
  column on market `instruments` via `MarketFacade`, or a research-owned table) -> ADR
- [ ] Check current SEC EDGAR fair-access rules (requests per second, `User-Agent` with contact), submissions API
  and archive URLs; `SEC_USER_AGENT` in `.env.example`
- [ ] Dependency (ask first): HTML -> text parser for filing documents (e.g. jsoup; alternatives: Spring AI
  `JsoupDocumentReader` / Tika reader, hand-rolled); verify version on Maven Central
- [ ] `research` layers via `new-bounded-context` (facade exists): domain `Filing`, `FilingType`, `AccessionNo`,
  `FilingSection` (item id, title, text), `SectionPolicy` (which items per form); ports `FilingSource`,
  `FilingRepository`
- [ ] Flyway V17 `research_filings` (accession unique, ticker, CIK, form, filed_at, period, source URL, content hash,
  screening result) + `research_filing_sections`
- [ ] EDGAR adapter (`RestClient`, `User-Agent`, rate limit, `RetryTemplate` on 429/5xx/timeout per ADR 0005);
  WireMock tests with saved submissions JSON + one trimmed 10-K / 10-Q / 8-K HTML each
- [ ] Section extraction (10-K Items 1, 1A, 7, 7A; 10-Q Part I Item 2, Part II Item 1A; 8-K Items 2.02, 7.01, 8.01):
  table of contents skipped, tables kept as text or dropped (decide in G1); `SectionExtractorTest` on the fixtures
- [ ] `ComplianceFacade.screenUntrustedContent` per section at ingest (ADR 0013 pattern list); suspected sections
  stored but excluded from retrieval
- [ ] `ResearchFacade.ingestFiling(ticker, accessionNo)` + `ingestFilings(ticker)` (idempotent by accession /
  content hash); facade `*IT`; job `filing-ingest` in `JobName` (own lock group; admin endpoint + runner, not in the
  morning pipeline); `docs/api.http` (happy path, 404 unknown job, 400)

### Slice 3 — Chunking, embeddings, vector store
- [ ] Decide C1 chunking: by section + size limit (DESIGN 8 proposal) vs fixed size; chunk size and overlap in
  tokens, how tokens are counted (tokenizer vs char estimate), section title prefixed to each chunk ("contextual"
  header), minimum chunk size -> ADR
- [ ] Decide V1 embedding model: Ollama `nomic-embed-text` (DESIGN 8 proposal) vs `mxbai-embed-large` / others
  (check current Ollama library); dimensions, max input length, task prefixes (`search_document:` /
  `search_query:` for nomic); finance-model comparison stays an eval experiment (slice 9) -> ADR
- [ ] Decide V2 embeddings without Ollama: test, CI and `demo` (deterministic fake `EmbeddingModel`, recorded query
  embeddings in `evals/replay/`, or an Ollama service container in CI) -> ADR (extends ADR 0015)
- [ ] Decide V3 vector storage: own table + JDBC (Flyway owns the schema) vs Spring AI `PgVectorStore`
  (`initialize-schema: false`); `vector` vs `halfvec`; HNSW `m`, `ef_construction`, query `ef_search`; cosine
  distance (`vector_cosine_ops`, `<=>`); embedding model name stored per chunk (vectors from different models are
  not comparable, re-embed on change) -> ADR
- [ ] Dependencies (ask first): `spring.ai.model.embedding: ollama` (existing Ollama starter); `com.pgvector:pgvector`
  if JDBC needs the type, or `?::vector` casts; tokenizer if C1 picks one; versions verified
- [ ] Domain `FilingChunk`, `Chunker` (pure; section boundaries, size, overlap) + `ChunkerTest`; port
  `EmbeddingPort` (batch, `TokenCount`); Ollama embedding adapter (timeout, batch size, retry); WireMock tests on the
  Ollama embed API (valid, wrong dimension, timeout)
- [ ] Flyway V18 `research_chunks` (filing, section, ordinal, text, token count, `embedding vector(N)`,
  `embedding_model`), HNSW index; `FlywayBaselineIT`
- [ ] `ingestFiling` chunks + embeds (embedding usage recorded in `ai_generation_log`? decide in V1: cost 0 locally,
  but latency and volume are useful); repository IT (insert, HNSW used per `EXPLAIN`)
- [ ] First local ingest of the G1 corpus: filings, sections, chunks, DB size, ingest time (in PROGRESS); load into
  Neon per G2
- [ ] D1 revisit (Domain Questions Log 2026-10-23): embed the 7 labelled same-event pairs in `evals/labelling/` and
  a sample of distinct pairs; cosine distribution; decide threshold and place (ingest vs classification) or keep
  trigram only -> ADR (supersedes part of ADR 0009)

### Slice 4 — RAG golden set and vector retrieval baseline
- [ ] Decide RE1 golden set: 40–50 questions ([DESIGN 10](DESIGN.md#10-evaluation-strategy)) incl. unanswerable ones
  ("I don't know" expected) and multi-ticker ones; expected sources as stable anchors (accession + item + quoted
  span, not chunk ids, which change on re-chunking); written before any retrieval output; drafted by Claude and
  reviewed by you (sample + agreement, as ADR 0010); filings public, so committed plain; news-based questions only
  via the encrypted path (ADR 0018) or synthetic news -> ADR
- [ ] Decide RE2 retrieval metrics and gate: recall@5 (DESIGN 10), MRR / nDCG as extra; which run is the baseline;
  CI gate "recall@5 >= baseline" runs where (every PR with recorded query embeddings per V2, or `workflow_dispatch`)
  -> ADR
- [ ] `evals/rag_golden.json` + `evals/labelling/` provenance README; anchor resolver (anchor -> chunk ids at eval
  time) + test
- [ ] `ResearchFacade.searchFilings(SearchQuery)`: metadata filters (ticker, form, filed-after), top-k, cosine;
  `SearchHit` (chunk, score, rank); port `ChunkSearch`; repository IT (filter + order)
- [ ] Retrieval eval runner (Java, no LLM, cost 0): `research-retrieval-eval` job; report in
  `evals/results/research-retrieval/` (recall@k per question, misses listed); first run committed as baseline
- [ ] `GET /api/research/search?q=&ticker=` (debug / UI side panel); `docs/api.http` (200, 400, empty); OpenAPI +
  UI types regenerated
- [ ] Update skill `new-eval-case` for the RAG golden set (anchors, unanswerable cases)

### Slice 5 — Hybrid search
- [ ] Decide H1: Postgres full-text (`tsvector`, `english` config, `ts_rank_cd`; not true BM25) vs a BM25
  extension (check what Neon supports); fusion: reciprocal rank fusion (k = 60) vs weighted scores; candidates per
  side; ticker / date filter applied to both sides -> ADR
- [ ] Flyway V19: generated `tsvector` column on `research_chunks` + GIN index
- [ ] `HybridRanker` (pure RRF) + `HybridRankerTest`; `searchFilings` mode `VECTOR | KEYWORD | HYBRID`
  (`briefwise.research.search.*`); repository IT (keyword hit a vector search misses, e.g. a ticker or a number)
- [ ] Retrieval eval for all three modes; comparison table (recall@5, MRR) in the ADR and README (DESIGN 8 "comparison
  in README"); new baseline if hybrid wins

### Slice 6 — Query rewriting and reranking
- [ ] Decide Q1 query rewriting: deterministic (ticker / company-name lookup, relative dates -> range) vs LLM
  structured output (`RewrittenQuery`: tickers, period, event types, search phrases) vs both; feature
  `research-rewrite`, model, budget -> ADR
- [ ] Decide RR1 reranking: none vs LLM reranker (top-20 -> top-5) vs cross-encoder (check Java / Ollama options);
  kept only if the eval shows a gain worth its cost and latency ([DESIGN 8](DESIGN.md#8-ai-and-engineering-decision-points)) -> ADR
- [ ] Rewriting via the `llm-adapter` skill if Q1 picks an LLM: `prompts/research-rewrite/` (v1 + hash guard),
  question in a delimited block after `screenUntrustedContent`, code gate (non-empty, length cap), replay fixtures,
  WireMock tests; otherwise `QueryRewriter` (pure) + tests
- [ ] Reranker adapter if RR1 keeps one (same skill, feature `research-rerank`)
- [ ] Retrieval eval with rewriting / reranking on and off; results in the ADRs; new baseline if adopted

### Slice 7 — Cited answer (non-streaming)
- [ ] Decide A1 answer model (Haiku 5.5 vs Sonnet 5.5; effort, `maxTokens`), feature `research-answer`, daily budget
  -> ADR
- [ ] Decide A2 sources for "why is X down?": filing passages + classified news for the ticker (`NewsFacade`, last N
  days) + the daily move (`MarketFacade.getDailyMove`, `SourceType.MARKET_DATA`); max 5 passages in total
  ([DESIGN 12](DESIGN.md#12-non-functional-requirements)) or 5 filing passages + facts; order in the prompt (stable
  prefix first) -> ADR
- [ ] Decide A3 output shape and hallucination control: `claims[]` each with passage ids + a short verbatim quote;
  validator: cited ids subset of supplied ids, quote found in the passage (normalised), numbers in a claim found in
  its passages (deterministic, as DESIGN 10 number grounding); uncited / failing claims dropped; nothing left ->
  "I don't know"; one re-ask on invalid structure; `Confidence` derived how (retrieval scores, share of kept claims,
  model self-report) -> ADR
- [ ] Decide A4 compliance and audit: `compliance.checkText` on the answer (forecast questions such as "will it
  recover?" -> refusal, rule 8), disclaimer or not (RM-internal text), store Q&A in `research_answers` for audit and
  reload; budget check before the replay advisor (Domain Questions Log 2026-11-07, revisit here) -> ADR
- [ ] Prompt `prompts/research-answer/` via `llm-adapter`: system rules (answer only from passages, cite every claim,
  say "I don't know"), passages in `<passage id=...>` blocks after screening; `ResearchAnswer`, `Claim`,
  `AnswerValidator` (pure) + `AnswerValidatorTest` (fabricated id, quote not in passage, ungrounded number, all
  dropped)
- [ ] `ResearchFacade.ask(question)`: rewrite -> search -> answer -> validate -> compliance -> store; Flyway V20
  `research_answers` (if A4); generation log with `subject_id`; facade `*IT` (replay)
- [ ] `POST /api/research/answers` (JSON) + `GET /api/research/answers/{id}`; `UsageLimitExceededException` -> 429;
  `docs/api.http` (200, 404, 400, 429 note); OpenAPI + UI types
- [ ] Tests: `LlmResearchAnswererIT` (WireMock: valid, malformed + re-ask, fabricated citation, timeout, budget),
  `ReplayResearchAnswerIT`; recording run for `research-answer` (throwaway DB) -> `evals/replay/research-answer.jsonl`
- [ ] DESIGN 6.2 (`research` facade), 6.3 (`ResearchAnswer`, `Claim`), 7.3, 13.7 (research cost rows) updated

### Slice 8 — SSE streaming
- [ ] Decide ST1: what streams. Progress events (`step`: rewriting, retrieving, n passages, answering, validating)
  + one final validated `answer` event, vs streaming answer tokens before validation (faster first token, but
  unvalidated text reaches the RM); event names and payloads (`step`, `answer`, `done`, `error` with correlation
  ID); `SseEmitter` (MVC) vs `Flux`; server timeout; client disconnect (cancel the LLM call or let it finish and
  store); errors after the stream started (budget is checked before, so 429 stays a plain response) -> ADR
- [ ] `POST /api/research/answers/stream` (`text/event-stream`), calls `ResearchFacade.ask` with a progress
  listener (port, no web types in `application/`); `X-Correlation-ID` echoed
- [ ] Tests: `ResearchStreamControllerIT` (event order, final answer equals the stored one, error event, 429 before
  the stream); latency per step logged (DESIGN 12 target < 10 s)
- [ ] `docs/api.http` stream request; OpenAPI note for the event payloads (schemas for the UI parser)

### Slice 9 — End-to-end RAG eval
- [ ] Decide RE3 answer metrics and gate: citation correctness and "I don't know" accuracy (deterministic, from the
  validator), faithfulness (LLM judge vs claim-level quote check only), judge model; judge calibration on 30–50
  answers scored by you ([DESIGN 10](DESIGN.md#10-evaluation-strategy)); CI threshold; Batch API -> ADR
- [ ] Answer eval runner (Java, reuses the production prompt and validator; Batch API like ADR 0018);
  budget `research-answer-eval`; `research-answer-eval` job; report in `evals/results/research-answer/`
- [ ] `.github/workflows/rag-eval.yml` (`workflow_dispatch` + path filter on `prompts/research-*/**`,
  `evals/rag_golden.json`; retrieval eval also on `research` search changes if RE2 says so); PR comment, artifact
- [ ] First live run; `baseline.json` for retrieval and answers; README section "RAG: vector vs hybrid vs reranked"
- [ ] Optional experiment (V1): a finance or hosted embedding model vs the chosen one on recall@5 (only if free or
  within budget; ask first)
- [ ] Skill for the retrieval / RAG pattern (e.g. `rag-retrieval`), now implemented once

### Slice 10 — UI: Research
- [ ] SSE parser over `fetch` + `ReadableStream` (ADR 0007, POST, `X-Correlation-ID`) + Vitest (split chunks,
  multi-line data, `error` event, abort)
- [ ] Research screen: question box, ticker filter, step progress, Stop (abort), answer with `AiMarker`, numbered
  citations; states: loading, empty, error (correlation ID), "I don't know", confidence, 429 budget, suspected
  injection excluded
- [ ] Citation side panel: filing / news / market source with the cited quote highlighted in the passage (text only,
  no HTML; `safeHref` for EDGAR links)
- [ ] Entry points: "Why is X down?" from a Portfolio position and a News item (prefilled question)
- [ ] Vitest for citation mapping + highlight (pure); `ui-verify` green

## Backlog (one line each)

Each milestone is split into numbered slices (like [M1](#m1-f2--news-pipeline)) before its first session; see the
Planning section in `CLAUDE.md`.

UI slices per milestone follow [ADR 0007](decisions/0007-ui-delivery-strategy.md). Every AI text carries the AI marker;
every screen has loading, empty and error states. Contingency cut order (nothing planned to be cut) is in the ADR.

- [ ] **M1 (F2)** News pipeline: see the [M1 section](#m1-f2--news-pipeline) above
- [ ] **M2 (F3)** Research (RAG over SEC filings and news): see the [M2 section](#m2-f3--research-cited-qa-over-sec-filings-and-news) above
  (D1 embedding revisit -> slice 3, prompt v2 + eval comparability -> slice 1, Research UI -> slice 10)
- [ ] **M3 (F4)** Volatility/VaR forecast, risk alerts, backtest, MLflow
  - [ ] Portfolio data rules before risk uses weights: stored vs derived weight (Domain Questions Log 2026-10-16),
    weights-sum invariant, rounding of the daily move, one policy for an unknown held instrument (2026-11-07) -> ADR
  - [ ] UI: forecast vs realised volatility chart (Recharts), VaR with model name/version, alerts list, alert count on Dashboard, position heatmap; mapper unit tests
- [ ] **M4 (F5)** Morning brief, approval workflow, MCP server, trajectory eval
  - [ ] Role `ADMIN` on `POST /api/admin/jobs/{job}/run` (open until login exists, ADR 0016)
  - [ ] UI: Login (httpOnly cookie + CSRF), route guard, role-based nav, 401/403 states; Brief screen (SSE progress, sections + citations, violations, edit, Approve disabled while violations open, Reject with reason, `isFallback` banner, change history); briefs awaiting approval on Dashboard; Playwright smoke (local); reassess server-state library
  - [ ] Summary ids (M1 follow-up 3): the upsert keeps the first row `id` while the domain makes a new UUID per
    save; decide when briefs start referencing summary ids
  - [ ] Jobs minor (found 2026-11-07): `JobsFacade` exposes the domain enums `JobTrigger` / `JobStatus` instead of view
    types (ADR 0006 1a); `briefwise.jobs.risk-alerts` / `brief` exist in config but not in `JobName` (until M3/M4)
- [ ] **M5 (F6)** Red-team, guardrails, MODEL_CARD / AI_RISK_ASSESSMENT / SECURITY
  - [ ] MODEL_CARD + README: eval labels drafted by Claude, maintainer as stand-in domain expert, label bias
    ([DESIGN 10](DESIGN.md#10-evaluation-strategy) "Domain expert in the loop")
  - [ ] UI: "suspected injection, content withheld" state + test; UI section in `SECURITY.md` (XSS rule, cookie auth)
- [ ] **M6 (F7)** Hardened pipeline, eval gating in CI, observability, demo video
  - [ ] `ai_generation_log` retention ([DESIGN 13.6](DESIGN.md#136-storage-retention)): full text 30 days, then hash + metrics only
  - [ ] Retention for the rest of the data (today nothing is ever deleted): decide what `news_items` (+ tickers,
    classifications), `news_outbox` (published rows), `news_portfolio_summaries`, `ai_daily_usage` and eval results
    keep and for how long (audit needs vs Neon free tier 0.5 GB); a scheduled cleanup job step; a storage-size check
    (warn before the Neon limit) -> ADR
  - [ ] Classification eval matrix (ADR 0018 backlog): Sonnet 5.5 comparison, Haiku without few-shot, Alpha Vantage baseline
  - [ ] UI: Admin (run now per job, token usage vs budget, cost summary + chart), last pipeline run on Dashboard, multi-stage Dockerfile serving the UI from Spring + `demo` Compose profile, Playwright in CI against `demo`, light mode, Evals screen
  - [ ] `docker-compose.yml` `demo` profile (M0 line ticked too early): app image + UI, with the UI item below
  - [ ] Optionally make `classification-eval` a required check (path-filtered: required checks stay pending on PRs
    that do not trigger it)
- [ ] **Production-readiness review (after M6, before calling the project done)** Briefwise is a one-person learning
  and portfolio project (GitHub Actions + Neon, <= 5 CHF/month, synthetic data). Write a full analysis (an ADR plus
  a doc, e.g. `docs/PRODUCTION_GAP.md`) of what would change for real use in a bank, comparing "today" vs
  "production" for each area:
  - Deployment and runtime: always-on services, containers/Kubernetes, environments (dev/test/prod), blue-green, IaC.
  - Scaling and availability: stateless app, DB replicas, Kafka cluster (multi-broker), SLOs, DR/backups.
  - Data: real market data licences (Yahoo is not allowed for commercial use), adjusted-close handling and
    corrections, lineage, retention, client data (real portfolios, PII), data residency (e.g. Swiss/EU).
  - Security and compliance: identity (SSO/OIDC, RBAC for RMs), secrets management, audit trail, encryption,
    pen-test, FINMA / GDPR / model-risk rules, vendor risk for LLM providers (data leaves the bank?).
  - AI specifics: private/self-hosted or enterprise LLM contract, eval gating, drift monitoring, human-approval audit.
  - Observability, performance and quality (gaps found 2026-11-01): metrics beyond `health` (Micrometer/Prometheus:
    classification latency, fallback rate, consumer lag, budget use), alerting and SLOs (today only GitHub's email on
    a failed run), measuring the DESIGN 12 latency targets (nothing measures them) and load tests, resilience beyond
    timeout + retry (circuit breaker, ADR 0005/0012), coverage (JaCoCo), static analysis (Checkstyle/Spotless,
    SpotBugs/Error Prone, Sonar), security scans in CI (CodeQL, dependency scan, secret scan, SBOM), DB backups and
    restore drills (Neon), migration rollback strategy, API rate limiting.
  - Tools and technologies: re-check each choice (Postgres+pgvector vs dedicated vector DB, Resilience4j, MLflow,
    observability stack, CI/CD, Spring Boot upgrade policy) and cost at real volume.
  - Output: a table of decisions to revisit, risks and an estimated effort; do not change DESIGN without asking.

## Domain Questions Log

Doubts about the domain model. Add new ones at session end; resolve them in an ADR or DESIGN update.

| Date | Question | Status |
|---|---|---|
| 2026-10-16 | Could `MorningBrief` (sections, citations, status history) become a God object? Candidate split: `BriefDraft` vs `ApprovedCommentary` ([6.3](DESIGN.md#63-domain-model-sketch)) | Open (decide at M4) |
| 2026-10-16 | `Position.weight` is stored. Should it be derived from quantity x latest price (stale-weight risk once prices move)? | Open (decide with M3 risk work) |
| 2026-10-16 | Risk profile lives on the client. What if one client has portfolios with different mandates? Ties to DESIGN 18 "brief per portfolio or per client" | Open (decide at start of M4) |
| 2026-10-16 | Facade view records duplicate domain records. Keep per-context views, or share read models via `shared`? | Open (revisit when `risk`/`brief` consume them) |
| 2026-10-18 | Snapshot `close` is the adjusted close, so a refresh rewrites history, but `importSnapshot` is `DO NOTHING`. Add an upsert mode, or always reset `daily_prices` before re-import? | Open (decide with the first refresh after M0 / M3) |
| 2026-10-18 | The instrument universe lives in `quant/universe.py` and in Flyway V4/V6. Single source, or a test that compares them (today only the snapshot-to-DB direction is tested)? | Open |
| 2026-10-18 | Approve is blocked while compliance violations are open (ADR 0007). Should an RM be able to override with a written justification (common in banks; needs its own audit trail)? | Open (decide at M4) |
| 2026-10-19 | What is `Relevance` relative to: the ticker's materiality in general, or a specific portfolio (weight, mandate)? Decides whether classification is per item or per portfolio | Decided: per holder of the ticker, weighting in code ([ADR 0014](decisions/0014-classification-output-shape.md)) |
| 2026-10-19 | ETFs (6 of 36 instruments) likely have no company news on Finnhub. Do held ETFs get news (e.g. via top holdings or macro news), or none? | None for now ([ADR 0009](decisions/0009-news-dedup-and-etf-news.md)); strategy (general news as `MACRO` first) to decide at M4 |
| 2026-10-21 | Finnhub licence forbids redistributing data "or derived results". May the hand-labelled golden set (real headlines) be committed once the repo is public, or only IDs/hashes plus labels, or synthetic headlines? Exports are git-ignored until then | Decided: committed only gpg-encrypted, results hold metrics only ([ADR 0018](decisions/0018-classification-eval.md)) |
| 2026-10-21 | A near-duplicate is linked to one ticker's original. If its URL is later fetched for a second ticker it stays `SKIPPED` even though the original may not concern that ticker. Acceptable? | Open (check on the labelled sample) |
| 2026-10-23 | Same-event duplicates (e.g. "KFC switches from Pepsi to Coke" vs "KFC plans a massive soda change") score 0.09–0.49 on headline trigram similarity, far below 0.6. Trigram dedup only catches re-syndication. Do we need semantic (embedding) dedup, and where: ingest or classification? | Open (task in M2; 7 labelled pairs in `evals/labelling/`) |
| 2026-10-23 | `EventType` has no value for analyst rating / price-target changes (~20 labelled rows) or customer contracts (KFC/Coke). Add `ANALYST` and `CONTRACT`, or keep them in `OTHER`? | Decided: `ANALYST` added, contracts `PRODUCT`; 22 rows relabelled ([ADR 0014](decisions/0014-classification-output-shape.md)) |
| 2026-10-23 | Relevance is labelled per ticker ("would an RM whose client holds it want to see it?"), multi-ticker items once for the most affected ticker. Does the classifier output per ticker or per item? | Decided: per item + affected tickers ([ADR 0014](decisions/0014-classification-output-shape.md)) |
| 2026-10-24 | An item whose message ends in `news.raw.DLT` stays `RECEIVED` with its outbox row published, so nothing re-sends it. Who picks it up: a sweep of old `RECEIVED` items (e.g. in `classifyPending` or the job runner), a DLT replay tool, or both? | Decided: sweep in `classifyPending`, min-age 10m, cap 3 ([ADR 0016](decisions/0016-jobs-sweep-and-job-runner.md)) |
| 2026-10-24 | `NewsIngested` carries the first-fetched `ticker` (Kafka key, K2); DESIGN 6.4 sketches `NewsIngested(newsId, occurredAt)`. Update the DESIGN sketch? | Done: DESIGN 6.4 updated (2026-10-24) |
| 2026-10-21 | Should `ai_generation_log` reference the business object it was generated for (e.g. `newsId`, `briefId`)? Today only feature + correlation ID link a call to its input; an audit ("which call produced this classification?") may need a direct reference | Decided: nullable `subject_id` ([ADR 0014](decisions/0014-classification-output-shape.md)) |
| 2026-10-27 | The demo has no news (no Finnhub key). Seed it from `evals/replay/synthetic_news.sql` with matching replay fixtures, and where: a demo-only seed at startup or the M6 demo Compose profile? | Open (decide at M6, or earlier if the demo is shown) |
| 2026-10-28 | `sweep_attempts` counts every sweep pick, also a budget deferral (at most one per run). Over several budget-starved days a healthy item could reach `RETRIES_EXHAUSTED`. Count only failed attempts instead, or is the bound acceptable? | Open (watch the first weeks of pipeline runs) |
| 2026-10-28 | Should a stale `RECEIVED` item (e.g. several days old after a long outage) still be classified, or skipped as no longer relevant for the morning brief? | Summaries: excluded by the 24 h published window ([ADR 0017](decisions/0017-daily-portfolio-summary.md)); classification unchanged. Briefs: decide at M4 |
| 2026-10-28 | A summary point cites news ids, but the model can still state a fact (or number) that is not in the cited item. Do summaries need number grounding / a faithfulness check before M4, or is citation-by-id plus the RM's review enough for an internal text? | Open (decide with the M4 brief eval) |
| 2026-10-28 | The template fallback quotes third-party headlines verbatim, which can trip the recommendation rules ("Why you should buy Apple"). Violations are stored and shown. Should such points be dropped or reworded instead? | Open (watch real fallbacks) |
| 2026-10-28 | The summary is per portfolio; a client with several portfolios gets several summaries with overlapping news. Merge per client? Ties to DESIGN 18 "brief per portfolio or per client" | Open (decide at start of M4) |
| 2026-10-24 | A provider outage turns every item of that run into `UNCLASSIFIED` (`PROVIDER_ERROR`) for good. Should the sweep retry those too (with a cap), or is a visible but unlabelled item acceptable? | Decided: retried by the sweep within 48h, same cap ([ADR 0016](decisions/0016-jobs-sweep-and-job-runner.md)) |
| 2026-11-07 | `Portfolio` does not enforce "weights sum to 1"; only the V5 seed comment and `PortfolioFacadeIT` assume it, so a future write path could store 0.8 or 1.3. Enforce it in the domain (with a tolerance), in the DB, or not at all (e.g. allow cash as the remainder)? Ties to the stored-weight row (2026-10-16) | Open (decide at start of M3) |
| 2026-11-07 | `PortfolioDailyMove` rounds each contribution to 4 decimals before summing, so the total can differ from the exact weighted sum in the last digit. Keep per-position rounding (contributions add up to the shown total) or round only the total (more exact)? Matters once VaR / briefs quote portfolio numbers (number grounding) | Open (decide at start of M3) |
| 2026-11-07 | Unknown held instrument is handled two ways: `FacadeInstrumentMoves` treats it as "unpriced" (fail-soft, a data bug stays silent), `FacadeHeldInstruments` throws (fail-loud, ingest stops). One policy for both, e.g. fail-soft plus a warning / health check? | Open (decide at start of M3, before `risk` adds a third consumer) |
| 2026-11-07 | The classification `rationale` is AI text shown to the RM (feed, relevant news) but never goes through `compliance.checkText`; only the prompt asks for "no advice", and the 25-word limit is not enforced. Check it like the summary (rule 8 in CLAUDE.md), or accept it as RM-internal text that never reaches a client? | Open (decide before M4 brief reuses the labels) |
| 2026-11-07 | `NewsClassified` is published (`NewsProcessingService`) but nothing listens; the summary finds new HIGH items by DB query (`RegenerationGate`). Keep the event for a future consumer (M4 brief, M6 metrics), or drop it until one exists? | Open (decide at M4) |
| 2026-11-07 | Idempotency guarantees one stored result, not one LLM call: two concurrent deliveries (consumer + sweep, or a redelivery while the first call runs) can both pass `status = RECEIVED` and both pay. Accept (sweep min-age 10m), or claim the item first (e.g. a `CLASSIFYING` status or `SELECT … FOR UPDATE SKIP LOCKED`)? | Open (watch generation-log duplicates per `subject_id`) |
| 2026-11-07 | The budget gate runs before the replay advisor, so a replay hit (free) still needs a budget entry and is deferred when the budget is used up. Check the budget only for live calls? | Open (minor; revisit with the next LLM adapter in M2) |
| 2026-11-07 | The code gate calls `listPortfolios()` + `getInstrument()` per held ticker for every message, to compute COMPANY/FUND it does not need (N+1 per message). A held-tickers-only port, or a per-run cache? | Open (performance; revisit with M6 metrics or the production review) |
| 2026-11-07 | The only scheduled sweep is the weekday 04:30 UTC runner, but `provider-error-retry-window` is 48h: a Friday `PROVIDER_ERROR` is ~72h old on Monday and is never retried. Widen the window (e.g. 96h), count it in runs instead of hours, or accept? | Decided: window 96h ([ADR 0021](decisions/0021-job-run-limits-status-and-lock.md)) |
| 2026-11-07 | Items DLT'd or deferred in a run are younger than `min-age` when the sweep runs after the drain, so with one run a day they wait ~24h (72h over a weekend) and then fall outside the summary's 24h published window: they reach the feed but never a summary. Acceptable, a second sweep later in the run (after `min-age`), or a shorter `min-age` in the runner (lag is already 0 there)? | Open (watch the first pipeline runs) |
| 2026-11-07 | The `sweep` job step is `SUCCEEDED` even when every picked item `failed` (per-item exceptions are caught), so the runner exits 0 and GitHub sends no email (ADR 0016 promises failed runs are visible). A sweep stopped on deferral also stays `SUCCEEDED`, while `summarize` maps that to `SKIPPED`. Fail the step above a failure threshold, and map `stoppedOnDeferral` to `SKIPPED` like the summary? | Decided: `FAILED` when at least half the picked items failed, deferral `SKIPPED` ([ADR 0021](decisions/0021-job-run-limits-status-and-lock.md)) |
| 2026-11-07 | A failed regeneration overwrites a good summary: today's compliant summary is replaced by the template when a new HIGH item triggers a regeneration and the first call fails, or the re-ask still violates / defers (same UTC-date upsert). It heals on the next run, but the RM loses the compliant text in between. Keep today's compliant summary when the regeneration falls back, or accept (ADR 0017: a non-compliant draft must never be shown)? | Open (decide before M4 reuses summaries) |
| 2026-11-07 | "Run news now" (admin trigger) has no drain step, so `summarize` runs while the listener is still classifying the items just ingested. Today's summary is built without them, and the gate reopens it only for a HIGH item, so new MEDIUM news never reaches today's summary. Drain for the admin trigger too, skip `summarize` for admin runs, or accept? | Decided (2026-11-07): admin runs skip `summarize`; summaries come from the runner / scheduler |
| 2026-11-07 | `briefwise.job-runner.max-duration` (15m) bounds only publish and drain. A slow ingest, sweep (up to 200 sequential LLM calls) or summary can run until the workflow's 25-min timeout kills it, with no exit 1 from the app and no "leftovers for the next run" message. Give every step the deadline (stop between items), or treat max-duration as a wait limit only and document it? | Decided: deadline for sweep and summaries between items, ingest unbounded ([ADR 0021](decisions/0021-job-run-limits-status-and-lock.md)) |

## Open questions from DESIGN

See [DESIGN 18](DESIGN.md#18-open-questions). Not duplicated here.
