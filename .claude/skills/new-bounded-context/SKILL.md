---
name: new-bounded-context
description: Use when creating a new bounded context (package) in the Briefwise app, e.g. "add the news context" or when a feature needs a new top-level package under com.briefwise. Scaffolds domain/application/infrastructure, the public Facade and a facade test, and keeps ArchitectureTest green.
---

# New bounded context

Rules live in CLAUDE.md ("Architecture rules") and docs/DESIGN.md section 6.2. Follow them; do not copy them here.

## Steps

1. **Confirm the name** is one of the contexts in DESIGN 6.2 (portfolio, market, news, research, risk, brief,
   compliance, aiops, access, jobs, mcp, shared). If it is new, stop and ask; a new context needs an ADR (`adr` skill).
2. **Check the facade methods** proposed in the DESIGN 6.2 table. Present them to me and confirm before writing.
3. **Create the package tree** under `app/src/main/java/com/briefwise/{context}/`:
   - `domain/model/`, `domain/port/`, `domain/event/` (no Spring, JPA or external libraries)
   - `application/` with `{Feature}Service` as a **package-private** class implementing an inbound port
   - `infrastructure/persistence|messaging|ai|web/` only for the adapters needed now (do not create empty ones)
   - `{Context}Facade.java` in the context root: the only public class other contexts may import; it delegates
     to application services and contains no logic of its own
4. **Ports**: public interfaces in `domain/port/`. Outbound ports for repositories, AI, messaging.
5. **Persistence** (if needed): JPA entities and MapStruct mappers only in `infrastructure/persistence`.
   Schema via the `flyway-migration` skill. Ask before adding MapStruct or any other dependency.
6. **Tests** mirroring the main packages under `app/src/test/java/com/briefwise/{context}/`:
   - `*Test` unit tests for domain (no Spring) and application services (Mockito `mock()` on ports)
   - `{Context}FacadeIT`: `@SpringBootTest` + Testcontainers (Postgres with pgvector) skeleton
7. **Docs**: add `docs/api.http` section for the context if it has an endpoint (happy path, 404, 400).
8. **Verify**: run `./mvnw verify` in `app/`. `ArchitectureTest` must pass. If an ArchUnit rule fails, fix the
   code; never weaken the rule without asking.
9. **Track**: tick the task in docs/PLANNING.md; record new domain doubts in the Domain Questions Log.
10. Do not commit. Propose a message at session end (`session-wrap-up`).

## Reminders

- If the context calls an LLM, name each AI decision point (model, temperature, structured output) and wait for my
  decision. Explain each AI concept used.
- Cross-context calls go through the other context's facade only.
