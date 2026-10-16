---
name: adr
description: Use when an architecture or AI decision has been made or needs to be recorded in Briefwise (model choice, library choice, chunking, hosting, resilience, any item from DESIGN section 8). Creates the next numbered ADR in docs/decisions/ with context, options, decision and consequences.
---

# Architecture Decision Record

Process and format are defined in docs/decisions/0001-record-architecture-decisions.md. CLAUDE.md requires that
every AI decision point is named with options and trade-offs and decided by the user, not silently.

## Steps

1. **Confirm the decision is made.** If it is still open, present the options and trade-offs first (and the
   starting proposal from DESIGN 8, if any) and wait for my decision. Use status `Proposed` only if I ask.
2. **Find the next number**: list `docs/decisions/`, take the highest `NNNN` + 1 (four digits, never reuse).
3. **Create** `docs/decisions/NNNN-kebab-case-title.md` from this template:

   ```markdown
   # NNNN. Title

   - Status: Accepted
   - Date: YYYY-MM-DD

   ## Context
   Why a decision is needed; constraints; links to DESIGN sections.

   ## Options
   1. ... (with trade-offs, including cost)
   2. ...

   ## Decision
   What we chose and the key parameters.

   ## Consequences
   Positive, negative, follow-ups, when to revisit.
   ```
4. **Content rules**: at least two real options; state cost/latency/quality trade-offs; for decisions settled by
   evaluation, include dataset, metric, result and date; link DESIGN sections instead of copying them.
5. **Supersede, don't rewrite**: to change an accepted ADR, create a new one and set the old status to
   `Superseded by NNNN` (the only allowed edit to an old ADR).
6. **Link it**: add to docs/PROGRESS.md (Decisions) and tick the related item in docs/PLANNING.md. If DESIGN 8 or 18
   listed this as open, mention that in the final summary to me (do not edit DESIGN.md without asking).
7. Do not commit.
