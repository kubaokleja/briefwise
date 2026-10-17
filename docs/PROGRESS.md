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
