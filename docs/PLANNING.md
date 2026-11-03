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
- [ ] `docker-compose.yml` `demo` profile does not exist yet (the line above was ticked too early); app image + UI come in M6

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
- [ ] Recording run (you: `ANTHROPIC_API_KEY`, < 0.01 USD): export `evals/replay/news-classification.jsonl`, replace `classification-valid.json` with the recorded body, confirm cache read tokens
- [x] Skill `llm-adapter` (pattern now implemented once)

### Slice 6 — Inbound adapters and daily pipeline
- [x] Decide sweep shape, PROVIDER_ERROR retry, done condition, timeout exit code, admin auth before M4 -> [ADR 0016](decisions/0016-jobs-sweep-and-job-runner.md) (sweep = `classifyPending`, min-age 10m, batch 200, cap 3 -> `RETRIES_EXHAUSTED`, stop at first deferral; PROVIDER_ERROR reopened within 48h; Kafka consumer lag via `Admin`; exit 0/1/2; endpoint open until M4)
- [x] `jobs` context: `JobsFacade.run`, `JobService` (steps ingest -> publish -> drain -> sweep), `POST /api/admin/jobs/{job}/run`; `docs/api.http` (happy path, 404 unknown job, 400 malformed id)
- [x] Scheduler only when `briefwise.execution.mode=scheduled`, guarded by `jobs.<name>.enabled`
- [x] Job runner (`--briefwise.job=news-ingest`): waits for consumer lag 0 or `briefwise.job-runner.max-duration` (15m); exit code via `ExitCodeGenerator` + `BriefwiseApplication`; relay scheduler off in `job`
- [ ] Neon project + secrets `NEON_JDBC_URL`, `NEON_DB_USER`, `NEON_DB_PASSWORD`, `ANTHROPIC_API_KEY`, `FINNHUB_API_KEY` + variable `PIPELINE_ENABLED` (you); run `cloud,job` locally once against Neon
- [ ] Load the price snapshot into Neon once: `import-on-startup` is on only in `local`/`demo`, so `daily_prices` in
  `cloud` is empty and the daily move shows nothing (one-off run with the import enabled, or a documented command);
  note in the UI/docs that the daily move is the last snapshot day (2026-10-16) until M3 refreshes prices daily
- [x] `.github/workflows/morning-pipeline.yml`: news ingest + classification, Kafka service container, gated by `PIPELINE_ENABLED` (first `workflow_dispatch` after merge to `master`)
- [x] Sweep of old `RECEIVED` items: Flyway V14 (`sweep_attempts`, `RETRIES_EXHAUSTED`, partial indexes); `NewsSweepService`; repository + Kafka ITs (DLT'd item swept)
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
- [ ] Recording run for `news-summary` (you, with the slice 5 recording run; procedure in `evals/replay/README.md`)
- [x] Slice 10: `model` / `promptVersion` of the summary response are nullable, but the generated TS types say
  `string`; mark them nullable in the spec (or handle `null` in the UI)

### Slice 8 — Classification eval
- [x] Decide E1, E2, golden-set publication, matrix -> [ADR 0018](decisions/0018-classification-eval.md) (all three
  labels reported on all + random rows, only sentiment macro F1 gated at -3 pp vs `baseline.json`; Java batch runner
  reusing the production prompt; golden set committed only gpg-encrypted; first run Haiku 5.5 as is)
- [x] Human spot-check of the 22 `ANALYST` relabels (14 golden accepted, 14/14; `evals/labelling/README.md`)
- [x] `evals/labelling/build_golden.py` -> `evals/sentiment_golden.csv` (258 golden rows, mojibake repaired, label and
  few-shot leakage checks); `evals/scripts/{encrypt,decrypt}-golden.sh`; export command fixed for UTF-8
- [ ] Encrypt and commit `evals/sentiment_golden.csv.gpg` (you: choose the passphrase, secret `EVAL_DATA_PASSPHRASE`)
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
- [ ] First live run (you, ~0.02 USD; `evals/results/news-classification/README.md`), commit `latest.json` as
  `baseline.json`
- [ ] ADR: classification model choice from the eval (L2), from the first run's numbers
- [x] Skill `new-eval-case`
- [ ] You: optionally make `classification-eval` a required check (path-filtered: required checks stay pending on PRs
  that do not trigger it)

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

## Backlog (one line each)

Each milestone is split into numbered slices (like [M1](#m1-f2--news-pipeline)) before its first session; see the
Planning section in `CLAUDE.md`.

UI slices per milestone follow [ADR 0007](decisions/0007-ui-delivery-strategy.md). Every AI text carries the AI marker;
every screen has loading, empty and error states. Contingency cut order (nothing planned to be cut) is in the ADR.

- [ ] **M1 (F2)** News pipeline: see the [M1 section](#m1-f2--news-pipeline) above
- [ ] **M2 (F3)** RAG over SEC filings: pgvector + HNSW, hybrid search, citations, SSE, RAG eval
  - [ ] Revisit D1 headline dedup with embeddings (the embedding model and pgvector arrive here anyway): measure
    semantic similarity on the 7 labelled same-event pairs in `evals/labelling/` and a sample of distinct pairs,
    decide threshold and place (ingest vs classification) -> ADR (Domain Questions Log 2026-10-23)
  - [ ] UI: Research chat over `fetch` streaming (own SSE parser, Stop), citation side panel with highlighted excerpt, "I don't know" / confidence / 429 states; parser unit tests
- [ ] **M3 (F4)** Volatility/VaR forecast, risk alerts, backtest, MLflow
  - [ ] UI: forecast vs realised volatility chart (Recharts), VaR with model name/version, alerts list, alert count on Dashboard, position heatmap; mapper unit tests
- [ ] **M4 (F5)** Morning brief, approval workflow, MCP server, trajectory eval
  - [ ] Role `ADMIN` on `POST /api/admin/jobs/{job}/run` (open until login exists, ADR 0016)
  - [ ] UI: Login (httpOnly cookie + CSRF), route guard, role-based nav, 401/403 states; Brief screen (SSE progress, sections + citations, violations, edit, Approve disabled while violations open, Reject with reason, `isFallback` banner, change history); briefs awaiting approval on Dashboard; Playwright smoke (local); reassess server-state library
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

## Open questions from DESIGN

See [DESIGN 18](DESIGN.md#18-open-questions). Not duplicated here.
