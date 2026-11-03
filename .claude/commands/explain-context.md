---
description: Read-only walkthrough of one Briefwise bounded context (role, classes, flows, properties, AI topics, pitfalls)
argument-hint: <context> [sub-area]   e.g. aiops | news classification | news outbox
---

# Explain a bounded context: $ARGUMENTS

I want to understand the current code of the bounded context named above **before finishing M1 and
starting M2**. Teach it to me. This is a **read-only** session: do not edit code, docs or config, do not
commit, do not call any live LLM or external API. Running existing tests (`./mvnw -q -Dtest=... test` in `app/`)
is fine if it helps to show behaviour.

## How to research (before writing anything)

1. Read `CLAUDE.md`, then the parts of `docs/DESIGN.md` about this context (6.2 bounded contexts, 6.3 domain
   model, 6.4 events, the 7.x flow it takes part in, 11 compliance, 13 cost control if relevant).
2. Find every ADR in `docs/decisions/` that mentions this context or its classes, and the `docs/PLANNING.md`
   slices that built it (plus open items and Domain Questions Log rows about it).
3. Read the code itself, not from memory:
   - `app/src/main/java/com/briefwise/<context>/**` (all layers)
   - its tests in `app/src/test/java/com/briefwise/<context>/**`
   - its Flyway migrations in `app/src/main/resources/db/migration/`
   - every `application*.yml` key it binds (search for its `@ConfigurationProperties` / `@Value` / `@ConditionalOn*`)
   - related files outside `app/`: `prompts/`, `evals/`, `.github/workflows/`, `docs/api.http`, `ui/` screens that call it
4. Find who calls its facade (other contexts, controllers, jobs) and which facades/ports it calls.
5. If the context is large (e.g. `news`) and no sub-area was given, first list its sub-areas
   (e.g. ingest + dedup, outbox + Kafka, classification, sweep, summary, eval, feed) with a suggested order,
   and stop. I will run this prompt again per sub-area.

Every claim must point to `file:line`. If something DESIGN or an ADR describes is not in the code (or the
code differs), say so explicitly as a finding; do not fix it.

## What to write (in this order, Markdown, headings as below)

### 1. Role in one minute
What problem this context solves for the Relationship Manager, where it sits in the daily flow
(ingest -> classify -> summarise -> brief ...), and what it deliberately does **not** do.

### 2. Map of the code
A package tree (`domain/model`, `domain/port`, `domain/event`, `application`, `infrastructure/*`) with one line
per class: what it is and why it exists. Mark the public ones (facade, ports) and note that services are
package-private and why (ArchUnit rule).

### 3. The facade (public API)
Table: method | input -> output | who calls it | what happens inside (1 line) | transaction / side effects.

### 4. Main flows, traced
For the 2-4 most important flows: a numbered step-by-step trace through the classes (`file:line` per step)
and a small Mermaid sequence diagram. Include the failure paths (exception, retry, fallback, status set).

### 5. Domain model and states
Records, enums, invariants enforced in constructors, status machines (draw transitions), and which rules live
in pure domain policies vs in the service.

### 6. Data
Tables it owns (Flyway file + version), key columns, unique constraints and indexes and **why** each exists,
JSONB columns, how the JPA entity / JDBC query maps to the domain (MapStruct?).

### 7. Properties to look at
Table: property key | default in `application.yml` | override per profile (`local`, `cloud`, `job`, `demo`,
`test`, `eval`) | bound in (class:line) | what changes if I change it | related ADR.
Include feature toggles, schedules, budgets, timeouts, retry settings, model names, prompt versions.

### 8. AI topics in this context
For each AI concept that appears here (e.g. structured output, few-shot, prompt caching, prompt versioning,
token budget gate, replay provider, re-ask + fallback, untrusted-content screening, generation log, evals,
Batch API, RAG, embeddings ...):
- **What it is** (plain explanation, assume I am learning)
- **Why here** and **where** (`file:line`)
- **Trade-off** and the alternatives that were rejected (ADR link)
- **How it is tested** (WireMock recording, replay fixture, eval)
If the context has no AI, say so and name the AI concepts it *supports* (e.g. budget data, compliance rules).

### 9. Points to watch
Gotchas a new developer would trip over: transaction boundaries, idempotency, ordering, concurrency
(`SKIP LOCKED`, conditional writes), time zones / UTC day, profile-dependent beans (`@ConditionalOn*`),
fail-closed vs fail-open behaviour, cost risks, security/compliance risks, tech debt and TODOs, open rows in the
Domain Questions Log, and any mismatch between code and DESIGN/ADRs found in step 3.

### 10. Connections
In: which contexts/jobs/controllers/UI call it. Out: which facades, ports, topics, events it uses.
A tiny dependency diagram.

### 11. Tests as documentation
Table: test class | type (unit / `*IT` / WireMock / Kafka / replay) | what behaviour it pins. Point out gaps.
Give the exact command to run just this context's tests.

### 12. M1 finish and M2 relevance
Which open M1 items in `docs/PLANNING.md` touch this context, and what M2 (RAG over SEC filings, pgvector,
embeddings, hybrid search, SSE, RAG eval, embedding dedup) will add to or change in it. Name any AI decision
points (DESIGN 8 / 18 IDs) that will come up, with options, but do not decide them.

### 13. Check yourself
6-8 questions I should be able to answer after this session (answers hidden in a collapsed
`<details>` block), and 1-2 small read-only exercises (run a specific test, follow a request from
`docs/api.http`, put a breakpoint at `file:line` and predict the value).

## Style
- English, concise, learner level: explain every term once the first time it appears.
- Prefer tables and traces over prose. No marketing language.
- Aim for something I can read in 20-30 minutes; link to code instead of pasting long snippets
  (short snippets of at most ~10 lines are fine when they make a point).
- End with: "Suggested next context:" following this order:
  `shared` -> `aiops` -> `compliance` -> `portfolio` -> `market` -> `news` (per sub-area) -> `jobs` ->
  skeletons (`access`, `brief`, `research`, `risk`) together -> cross-cutting (`ArchitectureTest`, profiles,
  logging/correlation ID, CI workflows).
