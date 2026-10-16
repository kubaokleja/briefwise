---
name: session-wrap-up
description: Use at the end of every Briefwise working session, or when the user says "wrap up", "end of session" or "log this". Verifies the build, updates docs/PROGRESS.md and docs/PLANNING.md, records domain questions and proposes a commit message without committing.
---

# Session wrap-up

Checklist source: CLAUDE.md ("Session end checklist") and docs/DESIGN.md section 17.

## Steps

1. **Check the state**: `git status` and `git diff --stat`. List what changed this session.
2. **Verify**: if application code changed, run `./mvnw verify` in `app/` (includes `ArchitectureTest`). Report
   failures honestly; do not wrap up as "done" on a red build. For docs-only sessions, say that nothing was built.
3. **Update docs/PROGRESS.md**: add a new entry at the top (below the template), using the template in that file:
   phase, goal, done, decisions (with ADR links), AI concepts introduced, domain questions, open questions, next
   session. Number the session after the latest entry and use today's date.
4. **Update docs/PLANNING.md**: tick completed items only if they are really done and verified; add newly
   discovered tasks under the right milestone.
5. **Domain Questions Log**: add any new domain-model doubts (e.g. possible God objects, unclear aggregate
   boundaries) with date, question, status.
6. **ADRs**: list decisions made this session without an ADR and offer to create them (`adr` skill).
7. **Open questions**: list anything from DESIGN 18 that became relevant, and new questions for me.
8. **Propose a commit message** (imperative, conventional prefix such as `feat:`, `docs:`, `test:`). Show it with
   the list of files. **Do not commit or push**; wait for my approval.
9. Finish with a short summary: done, not done, risks, what to start with next time.
