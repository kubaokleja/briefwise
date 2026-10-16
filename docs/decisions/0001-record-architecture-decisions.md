# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-10-16

## Context

Briefwise is a one-person project that doubles as a portfolio piece. Many choices (models, chunking,
retrieval, resilience library, hosting) are made over months, partly by evaluation. Without a record, the
reasoning is lost, and a reader (or a future session of Claude Code) cannot tell whether a choice was
deliberate. [DESIGN 8](../DESIGN.md#8-ai-and-engineering-decision-points) lists the decision points;
CLAUDE.md requires that none is decided silently.

## Options

1. **No formal record**: rely on DESIGN.md and git history. Cheap, but DESIGN describes the target, not why
   alternatives lost, and history is hard to search.
2. **One growing decisions document**: easy to find, but edits blur when a decision changed.
3. **ADRs, one file per decision** in `docs/decisions/`: immutable-ish, linkable, diffable.
4. **Wiki / external tool**: leaves the repo, not versioned with the code.

## Decision

Use option 3. Format:

- File `docs/decisions/NNNN-kebab-case-title.md`, four-digit sequential number, never reused.
- Header: title, `Status` (Proposed | Accepted | Superseded by NNNN | Deprecated), `Date`.
- Sections: Context, Options, Decision, Consequences.
- An accepted ADR is not rewritten; a change of mind creates a new ADR that supersedes it.
- Create ADRs with the `adr` skill. Link them from PROGRESS.md and, where relevant, DESIGN.md.
- Every AI decision point resolved by evaluation records the eval result (dataset, metric, date).

## Consequences

- Small overhead per decision; kept low by the skill and short template.
- Decisions become searchable and citable in the README and interviews.
- DESIGN.md stays the target description; ADRs hold the history of why.
- Open decisions in DESIGN 8/18 get an ADR only when decided.
