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
