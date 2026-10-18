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
- [x] `.github/workflows/ci.yml`: build, unit tests, ArchUnit, Testcontainers (Postgres + Kafka) (app job green on GitHub; Kafka IT comes with M1; `quant-verify` job added, first run pending)
- [x] Dependabot (Maven, GitHub Actions, uv done; npm entry added with `ui/`)
- [x] Python `quant/` with uv + ruff + pytest and a `quant-verify` CI job (7 offline tests; CI job not yet run on GitHub)

## Backlog (one line each)

- [ ] **M1 (F2)** News pipeline: Kafka ingest, outbox, dedup, LLM classification, generation log, replay, classification eval
- [ ] **M2 (F3)** RAG over SEC filings: pgvector + HNSW, hybrid search, citations, SSE, RAG eval
- [ ] **M3 (F4)** Volatility/VaR forecast, risk alerts, backtest, MLflow
- [ ] **M4 (F5)** Morning brief, approval workflow, MCP server, trajectory eval
- [ ] **M5 (F6)** Red-team, guardrails, MODEL_CARD / AI_RISK_ASSESSMENT / SECURITY
- [ ] **M6 (F7)** Hardened pipeline, eval gating in CI, observability, demo video
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

## Open questions from DESIGN

See [DESIGN 18](DESIGN.md#18-open-questions). Not duplicated here.
