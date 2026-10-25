# 0013. Untrusted content screening in M1

- Status: Accepted
- Date: 2026-10-24

## Context

News headlines and summaries are written by third parties and go into an LLM prompt. Indirect prompt injection
("Ignore previous instructions...") is the first threat in [DESIGN 11](../DESIGN.md#threat-model). The convention is
that untrusted content goes in delimited data blocks after `ComplianceFacade.screenUntrustedContent`
([DESIGN 12.1](../DESIGN.md#121-engineering-conventions)). In M1 the classifier has no tools and its output is a
fixed schema of enums plus a short rationale, so a successful injection can at worst produce a wrong label.

## Options

1. **Delimit only**: data block tags plus an instruction to treat its content as data. No false positives, no
   detection, no audit trail.
2. **Delimit + pattern list**: case-insensitive patterns for instruction overrides, role markers and our own
   delimiter tags. On a match: no LLM call, item `UNCLASSIFIED` with reason `INJECTION_SUSPECTED`, and an
   `InjectionSuspected` event. Cheap and deterministic, catches only known phrasings.
3. **LLM-based detector**: a second model call per item. Catches paraphrases, but costs a call per item and is
   itself injectable.

## Decision

Option 2. `ComplianceFacade.screenUntrustedContent(contentId, source, text)` returns whether it is suspected and
which rules matched. It publishes `InjectionSuspected(contentId, source, occurredAt)` in-process (no listener yet;
the M6 admin view will read them). The news prompt puts each item in a `<news_item>` block. The pattern list
matches our delimiter tags, so an item cannot close its block. Rules are kept in code with unit tests per pattern,
including headlines that must not match.

## Consequences

- A false positive costs one unclassified headline in the feed (still visible to the RM).
- Paraphrased injections pass. Acceptable while the model has no tools and the output is schema-bound; revisit
  before M2/M5 (filings in RAG, agent with tools), where option 3 or a classifier model may be needed.
- The same facade method screens filings in M2.
