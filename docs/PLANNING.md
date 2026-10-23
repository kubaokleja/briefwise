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

### Slice 1 — aiops foundation (no LLM)
- [x] Decide B1 budget unit, gate shape, day boundary -> [ADR 0008](decisions/0008-llm-daily-budget-gate.md) (USD per feature per UTC day, pre-check + post-record, fail closed)
- [x] Flyway: `ai_generation_log` (prompt, response, model, `prompt_version`, tokens, cost, latency, correlation ID) (V7), daily usage per feature (V8 `ai_daily_usage`)
- [x] Model pricing in `application.yml` (`briefwise.aiops.pricing`, Haiku 5.5 + Sonnet 5.5); cost per call (`ModelPrice`)
- [x] `AiopsFacade.recordGeneration`, `ensureBudgetAvailable` (added, ADR 0008), `checkAndRecordUsage` (daily budget per feature, B1), `getCostSummary`
- [x] `UsageLimitExceededException` -> HTTP 429 ProblemDetail (job step marked skipped: slice 6)
- [x] Unit tests + facade `*IT`; no aiops endpoint until the M6 Admin UI, so no `api.http`

### Slice 2 — News domain, Finnhub ingest, dedup
- [x] Decide D1 dedup threshold + window -> [ADR 0009](decisions/0009-news-dedup-and-etf-news.md) (0.6 within 48 h, same ticker; near-duplicates stored `SKIPPED`; ETFs skipped for now)
- [x] Check current Finnhub free-tier limits and licence (~60 calls/min, 30/s cap, NA companies, 1 year history; personal use, no redistribution); `FINNHUB_API_KEY` in `.env.example` (GitHub Secret: slice 6, with the workflow)
- [x] Domain: `NewsItem`, `NewsStatus`, `NewsClassification`, `Sentiment`, `Relevance`, `EventType` ([6.3](DESIGN.md#63-domain-model-sketch)); plus `FetchedNews`, `UrlHash`, `DedupPolicy`, `IngestPolicy`, `HeldInstrument`, `IngestSummary`
- [x] Flyway V9: `news_items` (URL hash unique) + `news_item_tickers`; no trigram index on headline (not used by the dedup query, ADR 0009)
- [x] Finnhub adapter (`RestClient`, key in header, `RetryTemplate` on 429/5xx/timeout per ADR 0005) for held companies only; WireMock tests (wiremock-standalone 3.13.2)
- [x] Dedup: URL hash + pg_trgm headline similarity (D1); `NewsRepositoryAdapterIT` pins the example pairs
- [x] `NewsFacade.ingest` (+ `MarketFacade.getInstrument` for the ETF check); facade `*IT`
- [ ] Export ~200 headlines for hand labelling (golden set 150 + separate few-shot pool), labelled before seeing model output (script + instructions in `evals/labelling/` done and tested on synthetic rows; needs your Finnhub key, a 30-day ingest and the labelling itself)

### Slice 3 — Outbox and Kafka
- [ ] Decide K1 outbox relay vs Debezium ([DESIGN 18](DESIGN.md#18-open-questions)), K2 Kafka key for multi-ticker news, K3 idempotency store (processed-messages table vs status check) -> ADRs
- [ ] Dependencies: spring-kafka, testcontainers-kafka (verify versions)
- [ ] Flyway: `outbox` table; news item + outbox row in one transaction
- [ ] Polling relay -> `news.raw` (key per K2); re-publishes pending rows on the next run
- [ ] Idempotent consumer by `newsId` (K3); retries -> `news.raw.DLT`
- [ ] Testcontainers Kafka IT: happy path, duplicate delivery, poison message -> DLT
- [ ] Skill `kafka-consumer` (pattern now implemented once)

### Slice 4 — Classification: first LLM adapter by hand
- [ ] Decide L1 structured output mechanism (`BeanOutputConverter` vs native structured output vs forced tool call), L2 model (start Haiku 5.5; Ollama dev model), L3 few-shot, L4 temperature + `maxTokens`, L5 prompt caching (verify current minimum cacheable prefix and TTL), L6 M1 injection screening scope, R1 circuit breaker / time limiter gap vs [ADR 0005](decisions/0005-resilience-spring-retryable.md) -> ADRs
- [ ] Dependencies: Spring AI 2.0.x Anthropic + Ollama starters (verify APIs in Spring AI 2.0 docs); model names in `application.yml` only
- [ ] Prompt in `prompts/news-classification/` with `PROMPT_VERSION`; stable prefix first, news in a delimited data block
- [ ] `ComplianceFacade.screenUntrustedContent` (M1 scope per L6)
- [ ] Code gate: ticker held, not duplicate, budget left
- [ ] Classification -> `NewsClassification`; invalid structure -> retry -> `UNCLASSIFIED`; generation log + `checkAndRecordUsage`; `NewsClassified` event
- [ ] `NewsFacade.classifyPending`; Kafka consumer calls it
- [ ] WireMock tests with recorded responses: valid, malformed, timeout

### Slice 5 — Replay provider
- [ ] Decide L7 normalisation of the replay key `hash(model, prompt_version, normalized input)`
- [ ] `ChatModel` decorator; `AiopsFacade.findReplay`; `on-miss: fail | fallback`
- [ ] Export replay fixtures to `evals/replay/`; `test` and `demo` use `replay`
- [ ] Skill `llm-adapter` (pattern now implemented once)

### Slice 6 — Inbound adapters and daily pipeline
- [ ] `jobs` context: `POST /api/admin/jobs/{job}/run`; `docs/api.http` (happy path, 404 unknown job, 400)
- [ ] Scheduler only when `briefwise.execution.mode=scheduled`, guarded by `jobs.<name>.enabled`
- [ ] Job runner (`job` profile, `--briefwise.job=news-ingest`): exits when consumer lag is 0 or after `max-duration`
- [ ] Neon project + `NEON_DATABASE_URL` secret (you); run the `job` and `cloud` profiles for the first time
- [ ] `.github/workflows/morning-pipeline.yml`: news ingest + classification, Kafka service container, gated by `PIPELINE_ENABLED`

### Slice 7 — Daily portfolio summary
- [ ] Decide S1 model, trigger (pipeline vs on demand), M1 `compliance.checkText` scope -> ADR
- [ ] `NewsFacade.getRelevantNews`, `summarizeForPortfolio`; regenerate only when new HIGH-relevance news exists
- [ ] Compliance rules (no recommendations, no forecasts, citations, disclaimer); AI marker; `isFallback`
- [ ] WireMock + replay tests; facade `*IT`

### Slice 8 — Classification eval
- [ ] Decide E1 metric scope (sentiment only vs + relevance / event type), E2 harness (promptfoo vs Java runner vs Python + Anthropic SDK; Batch API support)
- [ ] `evals/sentiment_golden.csv` (150 hand-labelled); few-shot examples never from the golden set
- [ ] Runner via Message Batches API; macro F1, confusion matrix, cost per 1,000 items; optional Alpha Vantage baseline
- [ ] `.github/workflows/evals.yml` (`workflow_dispatch` + `prompts/**`, `evals/**`); results table as PR comment
- [ ] ADR: classification model choice from the eval (L2)
- [ ] Skill `new-eval-case`

### Slice 9 — UI: News
- [ ] react-router 8.4.0 (ADR 0007); router + nav in `AppShell`
- [ ] `AiMarker` component (+ Vitest)
- [ ] News feed: sentiment icon + label, relevance, event type, expandable rationale, `UNCLASSIFIED`, headline + link only; loading / empty / error
- [ ] "Run news now" button -> admin endpoint; 429 state

### Slice 10 — UI: Portfolio v0 and Dashboard v0
- [ ] Portfolio v0: positions, daily move, daily AI summary with AI marker
- [ ] Dashboard v0: portfolio tiles (daily performance, relevant news count)
- [ ] OpenAPI spec + generated types updated; `ui-verify` green

## Backlog (one line each)

Each milestone is split into numbered slices (like [M1](#m1-f2--news-pipeline)) before its first session; see the
Planning section in `CLAUDE.md`.

UI slices per milestone follow [ADR 0007](decisions/0007-ui-delivery-strategy.md). Every AI text carries the AI marker;
every screen has loading, empty and error states. Contingency cut order (nothing planned to be cut) is in the ADR.

- [ ] **M1 (F2)** News pipeline: see the [M1 section](#m1-f2--news-pipeline) above
- [ ] **M2 (F3)** RAG over SEC filings: pgvector + HNSW, hybrid search, citations, SSE, RAG eval
  - [ ] UI: Research chat over `fetch` streaming (own SSE parser, Stop), citation side panel with highlighted excerpt, "I don't know" / confidence / 429 states; parser unit tests
- [ ] **M3 (F4)** Volatility/VaR forecast, risk alerts, backtest, MLflow
  - [ ] UI: forecast vs realised volatility chart (Recharts), VaR with model name/version, alerts list, alert count on Dashboard, position heatmap; mapper unit tests
- [ ] **M4 (F5)** Morning brief, approval workflow, MCP server, trajectory eval
  - [ ] UI: Login (httpOnly cookie + CSRF), route guard, role-based nav, 401/403 states; Brief screen (SSE progress, sections + citations, violations, edit, Approve disabled while violations open, Reject with reason, `isFallback` banner, change history); briefs awaiting approval on Dashboard; Playwright smoke (local); reassess server-state library
- [ ] **M5 (F6)** Red-team, guardrails, MODEL_CARD / AI_RISK_ASSESSMENT / SECURITY
  - [ ] UI: "suspected injection, content withheld" state + test; UI section in `SECURITY.md` (XSS rule, cookie auth)
- [ ] **M6 (F7)** Hardened pipeline, eval gating in CI, observability, demo video
  - [ ] `ai_generation_log` retention ([DESIGN 13.6](DESIGN.md#136-storage-retention)): full text 30 days, then hash + metrics only
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
| 2026-10-19 | What is `Relevance` relative to: the ticker's materiality in general, or a specific portfolio (weight, mandate)? Decides whether classification is per item or per portfolio | Open (decide at M1 slice 4) |
| 2026-10-19 | ETFs (6 of 36 instruments) likely have no company news on Finnhub. Do held ETFs get news (e.g. via top holdings or macro news), or none? | None for now ([ADR 0009](decisions/0009-news-dedup-and-etf-news.md)); strategy (general news as `MACRO` first) to decide at M4 |
| 2026-10-21 | Finnhub licence forbids redistributing data "or derived results". May the hand-labelled golden set (real headlines) be committed once the repo is public, or only IDs/hashes plus labels, or synthetic headlines? Exports are git-ignored until then | Open (decide before slice 8 / before the repo goes public) |
| 2026-10-21 | A near-duplicate is linked to one ticker's original. If its URL is later fetched for a second ticker it stays `SKIPPED` even though the original may not concern that ticker. Acceptable? | Open (check on the labelled sample) |
| 2026-10-21 | Should `ai_generation_log` reference the business object it was generated for (e.g. `newsId`, `briefId`)? Today only feature + correlation ID link a call to its input; an audit ("which call produced this classification?") may need a direct reference | Open (decide at M1 slice 4) |

## Open questions from DESIGN

See [DESIGN 18](DESIGN.md#18-open-questions). Not duplicated here.
