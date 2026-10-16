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
- [ ] Move IntelliJ skeleton (`pom.xml`, `src/`) into `app/`; rename `org.example` -> `com.briefwise`; Maven wrapper
- [ ] Verify current GA versions (Java 25, Spring Boot 4, Spring AI 2.0.x); confirm each dependency with me
- [ ] Decide resilience library (Resilience4j vs `@Retryable`) -> ADR
- [ ] `application.yml` with `briefwise.*` properties ([13.2](DESIGN.md#132-execution-modes-feature-toggles)); `BriefwiseProperties`
- [ ] Profiles `local`, `cloud`, `job`, `demo`, `test`
- [ ] Structured logging (human-readable local, JSON in `cloud`/`job`) + `X-Correlation-ID` filter and MDC

### Architecture
- [ ] Package skeleton for the 12 contexts ([6.2](DESIGN.md#62-bounded-contexts)) via `new-bounded-context`
- [ ] `shared` kernel: `Ticker`, `Money`, `Citation`, `TokenCount`
- [ ] `ArchitectureTest` (domain purity, facade-only access, package-private services, public only facades/ports)

### Data
- [ ] Flyway baseline; `ddl-auto: validate`
- [ ] `portfolio`: clients, portfolios, positions; 3-5 synthetic portfolios seeded
- [ ] `market`: instruments, prices, returns; ~30 US companies + a few ETFs; price source decision (Stooq vs yfinance)
- [ ] Facades: `PortfolioFacade` (`getPortfolio`, `listPortfolios`, `getHoldings`), `MarketFacade` (`getPriceHistory`, `getDailyMove`)
- [ ] Facade `@SpringBootTest` + Testcontainers tests (`*IT`); `docs/api.http` for portfolio and market

### Infrastructure and CI
- [ ] `docker-compose.yml`: Postgres + pgvector, Kafka (KRaft), Ollama; `demo` profile
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

## Open questions from DESIGN

See [DESIGN 18](DESIGN.md#18-open-questions). Not duplicated here.
