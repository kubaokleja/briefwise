# Planning

Task tracker. Source of truth for scope: [DESIGN.md](DESIGN.md). Tick boxes at the end of each session
(`session-wrap-up` skill). AI decision points are listed in [DESIGN 8](DESIGN.md#8-ai-and-engineering-decision-points).

## M0 (F1) — Skeleton

Goal: bounded contexts, synthetic portfolios, market data, CI from the first commit
([DESIGN 5](DESIGN.md#5-features-and-milestones)).

### Documentation and Claude Code setup
- [ ] `CLAUDE.md`
- [ ] `docs/PLANNING.md`, `docs/PROGRESS.md`
- [ ] ADR 0001 record-architecture-decisions
- [ ] ADR 0002 execution-modes-and-cost-control
- [ ] ADR 0003 runtime-github-actions-and-neon
- [ ] Skills: `new-bounded-context`, `flyway-migration`, `adr`, `session-wrap-up`
- [ ] `.gitignore`, `README.md` stub

### Project bootstrap
- [x] Move IntelliJ skeleton (`pom.xml`, `src/`) into `app/`; rename `org.example` -> `com.briefwise`; Maven wrapper
- [x] Verify current GA versions (Java 25, Spring Boot 4, Spring AI 2.0.x); confirm each dependency with me (Spring AI + Kafka deps deferred to M1)
- [ ] Decide resilience library (Resilience4j vs `@Retryable`) -> ADR
- [x] `application.yml` with `briefwise.*` properties ([13.2](DESIGN.md#132-execution-modes-feature-toggles)); `BriefwiseProperties`
- [x] Profiles `local`, `cloud`, `job`, `demo`, `test` (default + `test` exercised by tests; `cloud`/`job`/`demo` not yet run)
- [ ] Structured logging (human-readable local, JSON in `cloud`/`job`) + `X-Correlation-ID` filter and MDC (filter + MDC done and tested; JSON output config untested)

### Architecture
- [ ] Package skeleton for the 12 contexts ([6.2](DESIGN.md#62-bounded-contexts)) via `new-bounded-context` (done: `portfolio`, `market`; 10 to go)
- [ ] `shared` kernel: `Ticker`, `Money`, `Citation`, `TokenCount` (done: `Ticker`, `ResourceNotFoundException`)
- [x] `ArchitectureTest` (domain purity, facade-only access, package-private services, public only facades/ports); each rule proven to fail

### Data
- [x] Flyway baseline; `ddl-auto: validate`
- [x] `portfolio`: clients, portfolios, positions; 3-5 synthetic portfolios seeded (3 seeded)
- [ ] `market`: instruments, prices, returns; ~30 US companies + a few ETFs; price source decision (Stooq vs yfinance) (done: schema, 11 instruments; no price data, no source decision)
- [x] Facades: `PortfolioFacade` (`getPortfolio`, `listPortfolios`, `getHoldings`), `MarketFacade` (`getPriceHistory`, `getDailyMove`)
- [x] Facade `@SpringBootTest` + Testcontainers tests (`*IT`); `docs/api.http` for portfolio and market

### Infrastructure and CI
- [ ] `docker-compose.yml`: Postgres + pgvector, Kafka (KRaft), Ollama; `demo` profile (file written and `docker compose config` valid; stack not started yet)
- [ ] `.github/workflows/ci.yml`: build, unit tests, ArchUnit, Testcontainers (Postgres + Kafka)
- [ ] Dependabot (Maven, npm, uv, GitHub Actions)
- [ ] Python `quant/` placeholder with uv + lint/test in CI (can be empty-but-green)

## Backlog (one line each)

- [ ] **M1 (F2)** News pipeline: Kafka ingest, outbox, dedup, LLM classification, generation log, replay, classification eval
- [ ] **M2 (F3)** RAG over SEC filings: pgvector + HNSW, hybrid search, citations, SSE, RAG eval
- [ ] **M3 (F4)** Volatility/VaR forecast, risk alerts, backtest, MLflow
- [ ] **M4 (F5)** Morning brief, approval workflow, MCP server, trajectory eval
- [ ] **M5 (F6)** Red-team, guardrails, MODEL_CARD / AI_RISK_ASSESSMENT / SECURITY
- [ ] **M6 (F7)** Hardened pipeline, eval gating in CI, observability, demo video

## Domain Questions Log

Doubts about the domain model. Add new ones at session end; resolve them in an ADR or DESIGN update.

| Date | Question | Status |
|---|---|---|
| 2026-10-16 | Could `MorningBrief` (sections, citations, status history) become a God object? Candidate split: `BriefDraft` vs `ApprovedCommentary` ([6.3](DESIGN.md#63-domain-model-sketch)) | Open (decide at M4) |
| 2026-10-16 | `Position.weight` is stored. Should it be derived from quantity x latest price (stale-weight risk once prices move)? | Open (decide with M3 risk work) |
| 2026-10-16 | Risk profile lives on the client. What if one client has portfolios with different mandates? Ties to DESIGN 18 "brief per portfolio or per client" | Open (decide at start of M4) |
| 2026-10-16 | Facade view records duplicate domain records. Keep per-context views, or share read models via `shared`? | Open (revisit when `risk`/`brief` consume them) |

## Open questions from DESIGN

See [DESIGN 18](DESIGN.md#18-open-questions). Not duplicated here.
