---
name: flyway-migration
description: Use when adding or changing database schema in Briefwise (new table, column, index, seed data). Flyway is the only schema source; this skill covers naming, UUID keys, timestamps and verification with Testcontainers.
---

# Flyway migration

Conventions: CLAUDE.md ("Code" and "Architecture rules"), docs/DESIGN.md 12.1. Java owns the schema; Python
(`quant/`) only writes rows to `risk_forecasts` and never changes the schema.

## Steps

1. **Find the next version**: list `app/src/main/resources/db/migration/` and take the highest `V{n}` + 1.
2. **Name the file** `V{n}__{snake_case_description}.sql` (two underscores). One logical change per migration.
3. **Never edit an applied migration.** Fix forward with a new version. If the existing file was never run
   anywhere (not merged), ask me before editing it.
4. **Write the SQL** following the conventions:
   - Primary keys: `id UUID PRIMARY KEY` (generated in application code or `gen_random_uuid()`).
   - Every table has `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` and `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
   - Table and column names `snake_case`; names reflect the bounded context (prefix or clear noun).
   - Foreign keys across bounded contexts: ask first; contexts should reference each other by ID through facades,
     not by joins into another context's tables.
   - Extensions (`vector`, `pg_trgm`) in an explicit migration; vector indexes (HNSW) only when the RAG work begins.
   - Indexes only for known access patterns; state which query each index serves.
5. **Seed data** (synthetic portfolios, instruments) goes in a separate versioned migration or a profile-scoped
   seed; never real client data.
6. **Entities**: update JPA entities and MapStruct mappers in the context's `infrastructure/persistence`.
   Configuration must keep `spring.jpa.hibernate.ddl-auto: validate`; if validation fails, fix the migration or
   entity, never switch to `update`.
7. **Verify** with a Testcontainers (Postgres + pgvector) `*IT` that starts the app/context, runs all migrations
   from scratch and passes schema validation. Run `./mvnw verify` in `app/`.
8. If the change touches `risk_forecasts`, tell me: it is the Java-Python contract (DESIGN 6.1); the Python side
   in `quant/` must be updated together.
9. Tick the task in docs/PLANNING.md. Do not commit; propose a message at session end.
