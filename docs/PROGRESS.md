# Progress log

Newest entry first. Copy the template below for each session (`session-wrap-up` skill does this).

## Template

```markdown
## Session N — YYYY-MM-DD — <short title>

**Phase:** M? / task
**Goal:** one sentence

**Done**
- ...

**Decisions** (link ADRs)
- ...

**AI concepts introduced / explained**
- ...

**Domain questions added** (also in PLANNING)
- ...

**Open questions / blockers**
- ...

**Next session**
- ...

**Proposed commit message**
> ...
```

---

## Session 8 — 2026-10-23 — M1 slice 2: real ingest and labelled headline set

**Phase:** M1 / slice 2 (last task; no application code changed, no LLM calls from the app)
**Goal:** Run a 30-day Finnhub ingest, export headlines and label them for the golden set and few-shot pool.

**Done**
- Finnhub key in the git-ignored `.env` (the full 40-character key; each 20-character half returns 401).
- 30-day ingest against the local Compose Postgres: 1,908 new, 53 near-duplicates (`SKIPPED`), 255 already known,
  no failed tickers. Only the 9 companies held by the synthetic portfolios appear.
- Random export: 200 rows (150 golden / 50 few-shot). It was 84% `NEUTRAL`, 77% `LOW` and had only 5 `HIGH` golden
  rows, because Finnhub tags tickers loosely (most items are passing mentions or unrelated).
- Stratified company sample: `export_company_headlines.sql` (headlines naming the held company) and
  `append_company_sample.py` (stdlib; skips labelled rows, 16 per ticker, 3:1 golden/few-shot). New `sample` column
  (`random` / `company`). Total 344 rows: 258 golden, 86 few-shot. Golden: 18 `HIGH`, 58 `MEDIUM`, 19 `NEGATIVE`,
  38 `POSITIVE`; rare event types still thin (`GUIDANCE` 4, `REGULATORY` 3, `LITIGATION` 3, `M_AND_A` 2,
  `MANAGEMENT` 0).
- Labels drafted by Claude using the agreed definitions, spot-checked by the maintainer (about 40 rows, no disagreements). The
  provenance is recorded in `evals/labelling/README.md`, because an LLM-drafted golden set can share the
  classifier's biases.
- Label definitions written into `evals/labelling/README.md`. Fixed one stray `split` value with leading spaces.
- Checked the 7 labelled same-event pairs against pg_trgm: headline similarity 0.09–0.49, all below the 0.6 threshold.

**Decisions** ([ADR 0010](decisions/0010-golden-set-labelling.md))
- Relevance is judged relative to the single ticker ("would an RM whose client holds it want to see it this morning?").
- Multi-ticker items: one label from the view of the most affected ticker, named in `notes`.
- Sentiment = likely price effect for a shareholder, not tone; opinion/prediction pieces with no new facts are `NEUTRAL`.
- Golden set = random + stratified company sample; metrics reported per class and on `random` rows alone.

**AI concepts introduced / explained**
- Golden set independence: if the same kind of model labels the data and is evaluated on it, the eval measures
  agreement rather than correctness, so label provenance must be recorded and a human must review.
- Class imbalance vs macro F1: macro F1 averages per-class scores, so a class with 5 examples makes the metric noise.
  Stratified sampling fills rare classes; the `random` subset keeps the real distribution measurable.
- Lexical vs semantic dedup: trigram similarity catches re-syndicated headlines, not the same event in other words.

**Domain questions added** (also in PLANNING)
- Same-event duplicates are invisible to trigram dedup (0.09–0.49 on real pairs). Semantic dedup later?
- `EventType` has no home for analyst rating changes or customer contracts (both common). Add `ANALYST` / `CONTRACT`?

**Open questions / blockers**
- Event-type metric in slice 8 (E1): too few rare-class rows for macro F1; accuracy + per-class counts, or a longer
  ingest (free tier: 1 year) to collect rare events.
- The Finnhub key was pasted in chat; consider regenerating it.
- Publishing the golden set (licence) still open.

**Next session**
- M1 slice 3: decide K1 (outbox relay vs Debezium), K2 (Kafka key for multi-ticker news), K3 (idempotency store).

**Proposed commit message**
> chore(evals): add stratified company sample and label definitions for headline labelling

---

## Session 7 — 2026-10-21 — M1 slice 2: news domain, Finnhub ingest, dedup

**Phase:** M1 / slice 2 (no LLM calls)
**Goal:** Fetch company news for held tickers from Finnhub, drop exact and near-duplicates, store the rest as `RECEIVED`.

**Done**
- Finnhub checked: `company-news` is for North American companies, the free tier has 1 year of history, about
  60 calls/min (secondary sources) and a 30/s hard cap; the licence is personal use, no redistribution, and data
  must be deleted when the subscription ends.
- `news` domain: DESIGN 6.3 types plus `FetchedNews`, `UrlHash` (SHA-256 of the normalised URL), `DedupPolicy`,
  `IngestPolicy`, `HeldInstrument`, `IngestSummary`, `NewsSourceUnavailableException`. Ports `NewsIngest`,
  `NewsSource`, `NewsRepository`, `HeldInstruments`.
- `NewsIngestService`: held companies only (ETFs skipped), UTC lookback window (2 days), oldest first, per-ticker
  failures reported without stopping the run, misconfiguration (no key / 401) stops it.
- Flyway V9: `news_items` (unique `url_hash`, `duplicate_of_id` + CHECK that duplicates are `SKIPPED`) and
  `news_item_tickers` (one article, several tickers).
- `FinnhubNewsSource`: `RestClient` with socket timeouts, key in the `X-Finnhub-Token` header, Spring core
  `RetryTemplate` on 429/5xx/I/O (first use of ADR 0005; API checked in the Spring Framework 7 docs).
- `FacadeHeldInstruments` over `PortfolioFacade` + new `MarketFacade.getInstrument` (ETF check).
- `NewsFacade.ingest`; `NewsStartupIngest` (`briefwise.news.ingest.on-startup`, off by default) as a stopgap trigger.
- Tests: domain, service (Mockito, incl. UTC vs Zurich date), `FinnhubNewsSourceTest` (WireMock: OK, malformed
  items, 429 retry, 503, timeout, malformed body, 401, no key), `NewsRepositoryAdapterIT` (real pg_trgm, the D1
  example pairs), `NewsFacadeIT` (seeded portfolios end to end, rerun is idempotent). One shared WireMock server in
  `AbstractIntegrationTest` keeps a single Spring context. `./mvnw verify`: 118 unit + 51 IT green.
- `evals/labelling/`: export SQL (150 golden / 50 few-shot, fixed seed, round-robin over tickers) and instructions;
  tested on a throwaway container with synthetic rows. CSV exports are git-ignored.

**Decisions** (link ADRs)
- [ADR 0009](decisions/0009-news-dedup-and-etf-news.md): D1 = 0.6 within 48 h, same ticker; near-duplicates stored
  `SKIPPED` with `duplicate_of_id`; ETFs get no news for now (general news as `MACRO` is the first option for M4);
  no trigram index on the headline (the query is narrowed by ticker + window first).
- New test dependency: `org.wiremock:wiremock-standalone` 3.13.2 (latest stable; 4.0 is beta; shaded, no Jetty clash).
- `SimpleClientHttpRequestFactory` instead of the JDK client factory: the JDK factory's read timeout covers the
  whole body read and showed up as a non-retried "Stream closed" error.

**AI concepts introduced / explained**
- Trigram similarity (pg_trgm) as a free, deterministic near-duplicate detector, with a table of real scores; why
  the error direction matters (a wrong merge hides news, a missed duplicate costs a fraction of a cent);
  paraphrases need embeddings, deferred until the eval shows a need.
- Eval leakage: golden and few-shot rows split once with a fixed seed and never mixed; labels before model output.

**Domain questions added** (also in PLANNING)
- Can the golden set (real Finnhub headlines) be committed once the repo is public?
- A `SKIPPED` near-duplicate later fetched for a second ticker stays skipped for that ticker too.

**Open questions / blockers**
- You: get a Finnhub key, run the 30-day ingest and the export (`evals/labelling/README.md`), then label. Slice 4
  few-shot and slice 8 eval depend on it.
- The ingest has not run against live Finnhub yet; only WireMock with Finnhub's documented response shape.
- `FINNHUB_API_KEY` GitHub Secret: add it with the pipeline workflow (slice 6).

**Next session**
- M1 slice 3: decide K1 (outbox relay vs Debezium), K2 (Kafka key for multi-ticker news; note one item now has
  several tickers), K3 (idempotency store); spring-kafka + testcontainers-kafka (ask first); outbox table.

**Proposed commit message**
> feat(news): ingest Finnhub company news with URL and headline deduplication

---

## Session 6 — 2026-10-21 — M1 slice 1: aiops foundation (generation log, budget gate, cost summary)

**Phase:** M1 / slice 1 (no LLM calls)
**Goal:** Give every future LLM call a log entry, a cost and a daily USD budget gate.

**Done**
- Convention: every milestone is split into slices before its first session (CLAUDE.md "Planning", commit
  `9b844a4`). Following it, K1–K3 moved to slice 3 and D1 to slice 2.
- Flyway V7 `ai_generation_log`, V8 `ai_daily_usage` (UTC day x feature, unique key).
- `aiops` layers: domain (`ModelPrice`, `PriceList`, `DailyBudgets`, `BudgetStatus`, `DailyUsage`, `CostSummary`,
  `NewGeneration`), ports, package-private services, `AiopsRepositoryAdapter` (JPA insert for the log; atomic
  `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` for daily totals), `AiopsProperties` + `AiopsConfig`.
- `AiopsFacade`: `recordGeneration`, `ensureBudgetAvailable`, `checkAndRecordUsage`, `getCostSummary`.
- `UsageLimitExceededException` (in `shared`) -> HTTP 429 `ProblemDetail` with `feature`.
- Pricing in `application.yml` (Haiku 5.5 0.10/0.50, Sonnet 5.5 2/10 USD per 1M tokens, via the claude-api skill);
  budget `news-classification: 0.10` USD/day. Shared `ClockConfig` (UTC clock bean).
- Tests: domain, service (Mockito, incl. UTC-vs-Zurich date), `ApiExceptionHandlerTest` (429),
  `AiopsFacadeIT` (6 cases). `FlywayBaselineIT` expects 8 migrations. `./mvnw verify`: 81 unit + 43 IT green.

**Decisions** (link ADRs)
- [ADR 0008](decisions/0008-llm-daily-budget-gate.md): USD per feature per UTC day; pre-check + post-record;
  unknown feature/model fails closed.
- Daily totals stay on JDBC (upsert needs SQL; reads kept on the same row mapper). Accepted trade-off:
  `ai_daily_usage` is not covered by `ddl-auto: validate`, only by `AiopsFacadeIT`.
- No aiops HTTP endpoint until the M6 Admin UI.

**AI concepts introduced / explained**
- Tokens and per-token pricing (input vs output priced separately; output 5x on Haiku).
- Generation log as audit trail, later replay cache and eval data.
- Budget gate as a hard cap in code, before the provider's Console limit; overshoot of one in-flight call.
- Prompt-cache tokens priced differently: deferred to L5 (slice 4).

**Domain questions added** (also in PLANNING)
- Should `ai_generation_log` reference the business object it was generated for (news item, brief)?

**Open questions / blockers**
- L4 (slice 4): Haiku 5.5 rejects non-default `temperature` (HTTP 400), so DESIGN's "temperature 0" for
  classification is not possible as written.
- Prices are hand-copied (checked 2026-10-21); re-check when models change.

**Next session**
- M1 slice 2: decide D1 (dedup threshold + window) and the ETF news question, check Finnhub free tier, news domain,
  `news_items`, Finnhub adapter with WireMock (new dependency: ask first).

**Proposed commit message**
> feat(aiops): add generation log, daily USD budget gate and cost summary

---

## Session 5 — 2026-10-19 — Close M0: first CI run of all three jobs

**Phase:** M0 / loose ends (no feature code)
**Goal:** Get `app-verify`, `quant-verify` and `ui-verify` green on GitHub before M1.

**Done**
- Locally with npm 11.21.1 (CI's npm): `npm ci`, lint, typecheck, API types, 11 tests, build green; the lockfile
  written by npm 9 needed no regeneration (it already holds the Linux native bindings). `./mvnw verify` green.
- PR #2 (`feat/m0-completion` -> `master`): CI had not started because the PR conflicted with `master` (the CI setup
  was squash-merged as `8ca8c13`, the branch still had the original commit). Rebased onto `master`, so the duplicate
  was dropped and the history is linear (no merge commits).
- `quant-verify` failed: `astral-sh/setup-uv@v10` does not exist (setup-uv has no floating major tags after v7).
  Pinned `@v10.3.0`. All three jobs green on the PR (58 unit + 38 IT, 7 pytest, 11 Vitest).
- Environment: `gh` 2.102.0 installed (winget, `--source winget`), git `http.sslBackend=schannel` (Avast TLS).

**Decisions** (link ADRs)
- No ADR. Git workflow: linear history, rebase instead of merge; merge PRs with "Rebase and merge".

**AI concepts introduced / explained**
- None.

**Domain questions added** (also in PLANNING)
- None.

**Open questions / blockers**
- Add `quant-verify` and `ui-verify` as required checks in the `master` ruleset; consider "Require linear history".
- Local Node 24.14.1 < 24.15 (jsdom engine warning); stray `metrics-registry` npm config.

**Next session**
- Merge PR #2, then start M1 (news pipeline) with its decision points; UI slice: News feed, `AiMarker`, router.

**Proposed commit message**
> docs: record first green CI run of all three jobs

---

## Session 4 — 2026-10-18 — M0 UI skeleton, OpenAPI contract, ui-verify CI

**Phase:** M0 / UI skeleton ([ADR 0007](decisions/0007-ui-delivery-strategy.md))
**Goal:** A running `ui/` with design tokens, API client and a status page, typed from the backend's OpenAPI spec,
checked in CI.

**Done**
- PLANNING: hour estimates removed from the UI lines.
- Backend: springdoc 3.1.1 (`/v3/api-docs` in `local`/`test` only, sorted + pretty-printed), `OpenApiConfig`
  (relative server URL so the spec is machine-independent), `OpenApiSpecIT` (rewrites `docs/openapi.json` and fails
  on a difference), `server.address: 127.0.0.1` in `local`.
- `ui/`: Vite 8 + React 19 + TypeScript 5.9.3 + Tailwind 4, Node 24 (`.nvmrc`), Vite proxy `/api` + `/actuator`
  -> `127.0.0.1:8080`. DESIGN 15 tokens in `@theme`, self-hosted Inter / IBM Plex Mono.
- `api/client.ts` (correlation ID per request, `ProblemDetail` -> `ApiError` with the server-logged ID, status 0 =
  unreachable), `api/useApi.ts` (derived loading state, abort on unmount), generated `api/schema.d.ts` + `types.ts`.
- Components `AppShell`, `Amount` / `Percent` (arrow + colour for changes), `Loading` / `Empty` / `ErrorPanel`;
  `StatusPage` (API health + portfolios with largest position).
- ESLint (typescript-eslint, react-hooks 7 incl. React Compiler rules, ban on `dangerouslySetInnerHTML`, proven to
  fire on a throwaway file). Vitest: 11 tests (formatters, client, page success/empty/error).
- CI `ui-verify` job; Dependabot `npm` with groups and an ignore on TypeScript majors.
- Build: `./mvnw verify` green (58 unit incl. `ArchitectureTest`, 38 integration incl. `OpenApiSpecIT`); UI
  typecheck, lint, tests and build green. Real stack checked: through the Vite proxy `/api/portfolios` 200 with the
  correlation ID echoed, health `UP`, app listening on `127.0.0.1:8080` only. Stack stopped afterwards.

**Decisions** (link ADRs)
- Everything follows [ADR 0007](decisions/0007-ui-delivery-strategy.md). Small, no ADR: amounts render as
  `1,234.50 USD` (ISO code after the number); `Percent` takes percent points (weights are fractions, callers x100);
  OpenAPI generated with `--properties-required-by-default` because springdoc marks no record field as required.

**AI concepts introduced / explained**
- None (no LLM code). Groundwork for later AI screens: `ErrorPanel` shows the correlation ID that will also tag
  LLM calls in `ai_generation_log`, and the lint ban on HTML rendering protects against untrusted LLM output.

**Domain questions added** (also in PLANNING)
- None.

**Open questions / blockers**
- `ui-verify` has not run on GitHub yet; the lockfile was written by npm 9.7.2 while CI uses npm 11 (regenerate
  with a newer npm if `npm ci` fails).
- Local Node 24.14.1 < 24.15 wanted by a jsdom dependency (warning only); update Node, then Node 26 after 2026-10-28.
- Spec shows `*/*` response content (controllers declare no `produces`) and `riskProfile` as a plain string, not an
  enum. Decide when M1 controllers are written.
- `JAVA_HOME` / `JAVA_TOOL_OPTIONS` are user-level variables but were not inherited by this shell; set per command.

**Next session**
- Push and check `app-verify`, `quant-verify` and `ui-verify` on GitHub.
- Start M1 (news pipeline) with its decision points; UI slice: News feed, `AiMarker`, router.

**Proposed commit message**
> feat: add UI skeleton with OpenAPI-generated types and ui-verify CI

---

## Session 3 — 2026-10-18 — UI delivery planning (docs only)

**Phase:** M0 / planning (no application code)
**Goal:** Decide when and how each UI screen is built, with CI, tests and serving per profile.

**Done**
- Audit of DESIGN 15 vs PLANNING: no UI tasks, no login screen, no UI states, undefined AI-marker scope,
  `EventSource` cannot send auth/correlation headers, no Java/TS contract, no rule against rendering LLM output as
  HTML, `demo` Compose profile missing although ticked.
- UI tasks placed under M0-M6 in PLANNING (~54 h total); DESIGN 15, 12.1, 14.1, 14.2 and 16 updated.
- npm versions verified on the registry (listed in ADR 0007); springdoc 3.1.1 checked on Maven Central (built
  against Spring Boot 4.1.0).

**Decisions** (link ADRs)
- [0007](decisions/0007-ui-delivery-strategy.md) thin vertical slices; Spring serves the UI in `demo`; JWT in an
  httpOnly cookie + CSRF; OpenAPI-generated TS types; hand-rolled `useApi`; SSE progress for briefs; Approve
  blocked while violations are open; AI marker never removed; TypeScript pinned to 5.9.3; nothing planned to be cut.
- Dependencies approved for M0 (not installed yet): the M0 npm list in ADR 0007, springdoc-openapi 3.1.1,
  openapi-typescript 7.13.0.

**AI concepts introduced / explained**
- SSE token streaming: the LLM answer is sent piece by piece, so the RM reads within ~1 s instead of waiting
  ≤10 s. Trade-off: citation validation runs only at the end, so text is provisional until the `done` event.

**Domain questions added** (also in PLANNING)
- Should an RM be able to override open compliance violations with a written justification?

**Open questions / blockers**
- Milestone durations are not recorded anywhere, so the UI share per milestone cannot be stated as a percentage.
- Global npm is 9.7.2 (old for Node 24); CI uses the bundled npm.

**Next session**
- Build the M0 UI skeleton (PLANNING "UI skeleton"), then start M1.

**Proposed commit message**
> docs: add UI delivery strategy (ADR 0007) and plan UI slices per milestone

---

## Session 2 — 2026-10-18 — M0 completion: ADRs, remaining contexts, shared kernel, quant/ and price snapshot

**Phase:** M0 / last open items
**Goal:** Close M0: decisions as ADRs, skeletons for the remaining contexts, shared kernel, Python `quant/`,
market prices with a Java loader, and verification of the logging and Compose items left open in Session 1.

**Done**
- ADRs 0004-0006 (see below). Environment: TLS workaround replaced by a user-level `JAVA_TOOL_OPTIONS`
  (`Windows-ROOT` trust store, because Avast scans HTTPS), `UV_SYSTEM_CERTS=1` for uv; uv and Python 3.12 installed.
- Shared kernel: `SourceType`, `Citation`, `Money`, `TokenCount` (+ unit tests).
- Skeletons (empty facade + `*FacadeIT`) for news, research, risk, brief, compliance, aiops, access. `jobs` and `mcp`
  get no facade until their first adapter.
- `quant/` (uv, Python 3.12, pandas 3.0.6, yfinance 1.7.0, pytest, ruff): `universe.py`, `prices.py`
  (refresh script), 7 offline tests. `data/prices/`: 36 CSV files, 45,180 rows, 2021-10-18 to 2026-10-16.
- CI: `quant-verify` job; Dependabot entry for uv. (App job was already green; `quant-verify` not yet run on GitHub.)
- `market`: V6 (25 more instruments, 36 total), `PriceSnapshotImport` / `PriceSnapshotSource` ports, CSV reader,
  idempotent batch insert (`ON CONFLICT DO NOTHING`), `MarketFacade.importPriceSnapshot()`, startup import in
  `local` and `demo` only.
- `JsonLoggingIT`: JSON line with `correlationId` under the `job` profile.
- Compose stack started: Postgres healthy, Kafka started, Ollama answers; app in `local` profile starts on it
  (health UP, 36 instruments / 45,180 prices loaded). Containers stopped afterwards.
- Bug found only by starting the app for real: `Path`-typed config property with a relative default failed to bind
  in a web context. Now a `String` + `PriceSnapshotPropertiesTest` (web-context runner) as regression test.
- Build: `./mvnw verify` green: `ArchitectureTest` included, 37 integration tests.

**Decisions** (link ADRs)
- [0004](decisions/0004-market-price-source-yfinance-snapshot.md) yfinance refresh script + committed CSV snapshot
  (`close` = adjusted close; a refresh rewrites history, the import never updates stored closes).
- [0005](decisions/0005-resilience-spring-retryable.md) Spring core retry first, Resilience4j later.
- [0006](decisions/0006-facade-views-and-public-surface-conventions.md) facade view records, public-surface rules.
- Not in an ADR (small): `SourceType` values; snapshot import runs on startup only in `local`/`demo`.

**AI concepts introduced / explained**
- None new in code (no LLM calls yet). Data side of the RAG/forecast pipeline: prices come from a reproducible
  snapshot so the quant batch and tests never depend on a live market API.

**Domain questions added** (also in PLANNING)
- Adjusted close rewrites history on every refresh, but the import never updates: reset or add an upsert mode?
- Instrument universe is defined twice (`universe.py` and V4/V6): how to keep them in sync?

**Open questions / blockers**
- `quant-verify` CI job has not run on GitHub yet (push needed, done by you).
- `demo` profile not verified end to end; Ollama has no model pulled yet.
- `docs/DESIGN.md` has an uncommitted one-line change (blank line after the title removed) that was not made by
  this session; check whether you want it.
- Optional, awaiting approval: Jupyter as an optional dependency group in `quant/`; README note on running behind Avast.

**Next session**
- Start M1 (news ingestion and classification). First decisions to name: classification approach
  (workflow vs agent), model choice, structured output, Spring AI 2.0 setup (verify APIs in docs), Kafka topic design.
- Verify the Spring Framework 7 core retry claim in ADR 0005 when the first adapter is written.

**Proposed commit message**
> feat: complete M0 with remaining contexts, shared kernel, quant snapshot and price loader

---

## Session 1 — 2026-10-16 — M0 step 1: Spring Boot skeleton, architecture rules, portfolio + market

**Phase:** M0 / project bootstrap, architecture, data (first slice)
**Goal:** A green `./mvnw verify` on a Spring Boot 4 app with profiles, config, cross-cutting concerns, Flyway
baseline, ArchUnit rules and the first two bounded contexts.

**Done**
- Verified GA versions on Maven Central (Boot 4.1.1, Spring AI 2.0.1, Spring Kafka 4.1.1, Testcontainers 2.0.5,
  ArchUnit 1.5.1, Flyway 12.4.0, MapStruct 1.6.3, logstash-logback-encoder 9.0, Maven 3.10.0).
- Moved the IntelliJ skeleton into `app/` (`com.briefwise`), Maven wrapper 3.10.0, Surefire `*Test` / Failsafe `*IT`.
- `BriefwiseProperties` (execution.mode, jobs.*, llm.provider/replay, cors; config only), profiles
  local/cloud/job/demo/test, `local` default (proved by `DefaultProfileIT`), actuator health.
- Cross-cutting: `CorrelationIdFilter` (+ MDC, unsafe values replaced), CORS, `ApiExceptionHandler` (404/400 as
  ProblemDetail), `logback-spring.xml` (human-readable vs JSON).
- Flyway V1-V5: extensions (vector, pg_trgm), portfolio schema, market schema, 11 instruments, 3 synthetic portfolios.
- Contexts `portfolio` and `market` with facades, ports, package-private services, JPA + MapStruct adapters,
  controllers; `docs/api.http`.
- `ArchitectureTest` with 4 rules; each proven to fail on a deliberately bad class, then the classes were deleted.
- `docker-compose.yml` (pgvector 0.8.7-pg16, Kafka 4.3.1 KRaft, Ollama 0.40.5) and `.env.example`.
- Build: `./mvnw clean verify` green: 41 unit tests + 25 integration tests (Testcontainers).

**Decisions** (no ADR written yet; proposals below)
- Spring Boot 4.1.1; Spring AI 2.0.1 deferred to M1 (not needed while `llm.provider` is config only).
- Facades return nested view records (`PortfolioFacade.PortfolioView`, ...); other contexts may import only the
  facade and its nested types (strict reading of "the facade is the ONLY class").
- Rule 4 wording: public top-level classes of a context = facade, `domain.port`, `domain.model`, `domain.event`
  (domain types must be public for the adapters; rule 2 keeps other contexts out).
- Tickers are plain symbols across contexts (no FK from `positions` to `instruments`).
- Daily prices store `close` and `volume` only; OHLC/adjusted close wait for the price-source decision.

**AI concepts introduced / explained**
- Replay provider (`briefwise.llm.provider=replay`): a `ChatModel` decorator that answers from earlier recorded
  responses (looked up by hash of model + prompt version + input) instead of calling an LLM. Why here: CI and the
  demo become free and deterministic. Trade-off: it only knows prompts that were recorded, hence `on-miss: fail|fallback`.
  Config only so far; no behaviour.

**Domain questions added** (also in PLANNING)
- Position `weight` is stored; should it be derived from quantity x latest price (stale-weight risk)?
- Risk profile sits on the client; what if one client has portfolios with different mandates?
- Facade view records duplicate domain records: acceptable cost, or share read models via `shared`?

**Open questions / blockers**
- JSON logging (`cloud`/`job`) is configured but not exercised by any test yet.
- `docker-compose.yml` only validated with `docker compose config`; the stack was not started.
- Environment: Java's trust store does not trust this machine's TLS chain (curl and Maven fail). Worked around per
  session with `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` and `JAVA_HOME`; nothing was changed
  globally. `./mvnw` needs both in your shell.
- Resilience library ADR (Resilience4j vs `@Retryable`) still open.

**Next session**
- Remaining M0: other 10 context skeletons, shared kernel (`Money`, `Citation`, `TokenCount`), market price source
  and ~30 companies, resilience ADR, CI workflow, Dependabot, `quant/` placeholder, run the compose stack.

**Proposed commit message**
> feat: add Spring Boot 4 app skeleton with portfolio and market contexts

---

## Session 0 — 2026-10-16 — Documentation and Claude Code setup

**Phase:** M0 / documentation only (no application code)
**Goal:** Create CLAUDE.md, planning docs, first ADRs, skills, .gitignore, README stub.

**Done**
- CLAUDE.md, PLANNING.md, PROGRESS.md
- ADRs 0001-0003
- Skills: new-bounded-context, flyway-migration, adr, session-wrap-up
- .gitignore extended, README stub

**Decisions**
- Maven; app in `app/`; tests mirror main packages, `*Test` unit / `*IT` integration.
- [ADR 0001](decisions/0001-record-architecture-decisions.md), [0002](decisions/0002-execution-modes-and-cost-control.md), [0003](decisions/0003-runtime-github-actions-and-neon.md)

**AI concepts introduced / explained**
- None (no code).

**Open questions / blockers**
- Root `pom.xml` / `src/` (`org.example`) must be moved to `app/` and renamed `com.briefwise`.

**Next session**
- M0 bootstrap: move skeleton, verify versions, Spring Boot 4 app, `ArchitectureTest`.

**Proposed commit message**
> docs: add CLAUDE.md, planning docs, ADRs 0001-0003 and Claude Code skills
