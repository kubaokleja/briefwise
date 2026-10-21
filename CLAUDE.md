# Briefwise — Claude Code instructions

Source of truth: [docs/DESIGN.md](docs/DESIGN.md). If this file and DESIGN disagree, stop and ask.
Task tracker: [docs/PLANNING.md](docs/PLANNING.md). Session log: [docs/PROGRESS.md](docs/PROGRESS.md).
Decisions: [docs/decisions/](docs/decisions/) (ADRs).

## Project

Briefwise is an AI copilot for private banking Relationship Managers (RMs). It classifies news with an
LLM, forecasts volatility/VaR with a backtested Python quant batch, answers "why is X down?" with cited
SEC-filing evidence (RAG), and drafts compliance-checked client commentary that an RM approves.
Principle: *every number has a source, nothing reaches a client without human approval.*
Synthetic data only, no recommendations, no order execution, no auto-sending
([scope](docs/DESIGN.md#4-scope)). Budget: <= 5 CHF/month.

Stack ([full table](docs/DESIGN.md#16-technology-stack)): Java 25, Spring Boot 4, Spring AI 2.0.x, Maven,
PostgreSQL + pgvector + pg_trgm, Flyway, MapStruct, Kafka (KRaft), Resilience4j (pending ADR),
JUnit 5, Mockito, Testcontainers, WireMock, ArchUnit; Python 3.12+/uv for `quant/`; React + Vite for `ui/`.
Versions in DESIGN are hints: verify current GA versions before using them.

## Layout

Java package `com.briefwise`. Spring Boot app in `app/` (Maven), plus `quant/`, `ui/`, `prompts/`, `evals/`,
`.github/workflows/` ([tree](docs/DESIGN.md#17-repository-structure-and-claude-code-workflow)).
Note: the root `pom.xml` / `src/` (`org.example`) is an IntelliJ skeleton; the first M0 task moves it to
`app/` and renames the package. Until then do not build on it.

## Architecture rules (ArchUnit-enforced)

Details: [6.2 Bounded contexts](docs/DESIGN.md#62-bounded-contexts).

- Bounded contexts: portfolio, market, news, research, risk, brief, compliance, aiops, access, jobs, mcp, shared.
- Each context: `domain/{model,port,event}`, `application/`, `infrastructure/{persistence,messaging,ai,web}`,
  and one public `{Context}Facade`.
- The facade is the ONLY class another context may import. No reaching into another context's internals.
- `{Feature}Service` in `application/` is package-private. Only facades and ports are public.
- `domain/` is pure: no Spring, JPA or external libraries.
- Controllers, schedulers, job runners and MCP tools only call facades; zero business logic.
- `ArchitectureTest` (in `com.briefwise`) must stay green on every change. Never weaken a rule to make it pass;
  ask first.
- Quant (Python) talks to Java only through the `risk_forecasts` table. Java owns the schema (Flyway).

## Conventions ([12.1](docs/DESIGN.md#121-engineering-conventions))

**LLM calls**
- Prompts in English. Output always structured (`BeanOutputConverter` -> record); never parse free text.
- Every call goes to `ai_generation_log` (prompt, response, model, `prompt_version`, tokens, cost, latency).
  Changing a prompt means bumping `PROMPT_VERSION`.
- After every call: `TokenCount` -> `AiopsFacade.checkAndRecordUsage()`. Explicit `maxTokens` on every call.
- No client-identifying data in prompts; synthetic IDs only. Untrusted content (news, filings) goes in
  delimited data blocks after `compliance.screenUntrustedContent`.
- Invalid structure -> retry -> fallback with a flag. Stable prefix first (system, few-shot, tools), variable last.

**Code**
- Records for value objects, events, DTOs. JPA entities only in `infrastructure/persistence`; MapStruct mappers.
- Unchecked domain exceptions; `Optional` instead of `null` in domain/application.
- Flyway is the only schema source (`ddl-auto: validate`), UUID keys, `created_at`/`updated_at`.
- Structured logs (JSON in `cloud`/`job`); `X-Correlation-ID` in MDC for requests, jobs and LLM calls.

**Tests** (verify with tests, not manual `curl`)
- Tests mirror main packages. `*Test` = unit (Surefire), `*IT` = integration (Failsafe, Testcontainers).
- Domain: plain unit tests, no Spring. Application services: Mockito `mock()` on ports.
- AI adapters: WireMock with recorded responses (valid, malformed, timeout).
- Kafka: Testcontainers (idempotency + DLT). Facades: `@SpringBootTest` + Testcontainers (pgvector).
- `docs/api.http` per context: happy path, 404, 400.

**Profiles**: `local` (default, Docker Compose), `cloud` (Neon + Claude API), `job` (non-web runner),
`demo` (replay, no keys), `test` (Testcontainers, WireMock, replay).

## Cost rules ([13](docs/DESIGN.md#13-cost-control))

- NEVER call a live LLM in tests. CI uses WireMock recordings and the `replay` provider.
- Model names live only in `application.yml`, never in code.
- Code gate before every LLM call (ticker held, not duplicate, budget left). Never call an LLM without a reason.
- LLM evals only via `workflow_dispatch` or `prompts/**`/`evals/**` changes, through the Batch API.
- Never use Ollama for evals. Never commit API keys; secrets only in GitHub Secrets / `.env`.
- Scheduled work runs in GitHub Actions + Neon; no always-on infrastructure ([ADR 0003](docs/decisions/0003-runtime-github-actions-and-neon.md)).

## Standing instructions (always apply)

1. **Explain every AI concept** that appears in code (what it is, why here, trade-off), briefly, when you
   introduce it. Assume I am learning.
2. **Never commit without my approval.** Show the diff summary and a proposed message, then wait.
   Never push.
3. **Name every AI decision point** (model choice, temperature, chunking, retrieval, workflow vs agent, ...)
   with options and trade-offs, and let me decide. Starting proposals are in
   [DESIGN 8](docs/DESIGN.md#8-ai-and-engineering-decision-points). Record the outcome in an ADR (`adr` skill).
4. **Ask before adding any dependency** (Maven, npm, Python): name it, why, alternatives.
5. **Verify library versions** from Maven Central / official docs instead of memory. Spring Boot 4 and
   Spring AI 2.0 are newer than your training data: check the docs for APIs before using them.
6. Do not decide silently where DESIGN is silent or open ([18](docs/DESIGN.md#18-open-questions)); ask.
7. Skills are written only after the pattern was implemented once by hand. Do not create F2/F3 skills early.
8. Compliance rules in any text-producing feature: no recommendations, no forecasts/promises, citations
   for factual claims, fixed disclaimer, nothing leaves the system unless `Approved`
   ([11](docs/DESIGN.md#11-security-and-compliance)).

## Planning (milestones in slices)

Every milestone is split into numbered slices in `docs/PLANNING.md` before its first session (M1 is the reference).
- One slice per session, done in order; each ends with `./mvnw verify` green (UI slices: `ui-verify`).
- A slice is small and concrete: checkbox tasks naming the tables, facade methods, adapters and tests it adds.
- Decision points (DESIGN 8/18 IDs) are listed and settled at the start of the slice that first needs them -> ADR.
- New dependencies are named in the slice that introduces them; skills go in the slice after the pattern exists.
- UI slices come last in the milestone. Backlog milestones stay one line until they are split.

## Session start checklist

1. Read `docs/PROGRESS.md` (latest entry) and `docs/PLANNING.md`.
2. State: current phase/milestone, what was built last, today's task, open questions.
3. Confirm the plan with me before writing code; list AI decision points the task touches.

## Session end checklist

Run the `session-wrap-up` skill:
1. Build and tests green (`./mvnw verify` in `app/`) incl. `ArchitectureTest`.
2. Update `docs/PROGRESS.md` and tick `docs/PLANNING.md`.
3. Add new domain doubts to the Domain Questions Log in PLANNING.
4. Write ADRs for decisions made (`adr` skill).
5. Propose a commit message. Do not commit.

## Skills

`.claude/skills/`: `new-bounded-context`, `flyway-migration`, `adr`, `session-wrap-up`.
