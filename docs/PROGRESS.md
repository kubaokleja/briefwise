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
