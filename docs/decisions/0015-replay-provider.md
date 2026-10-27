# 0015. Replay provider: advisor, key normalisation, no logging of hits

- Status: Accepted
- Date: 2026-10-27

## Context

[ADR 0002](0002-execution-modes-and-cost-control.md) and [DESIGN 13.3](../DESIGN.md#133-llm-providers-and-replay)
call for a `replay` provider: recorded answers looked up by `hash(model, prompt_version, normalized input)`, so CI
and the demo cost nothing and give the same answer every run. Recordings are exported to `evals/replay/`
([13.6](../DESIGN.md#136-storage-retention)). Three questions were open: where the lookup sits, what the key
normalises (L7), and whether a replayed answer leaves a trace in `ai_generation_log`. A fourth: how to get real
recordings, since none existed (slice 4 fixtures were hand-written).

## Options

**Where the lookup sits**
1. **`ChatModel` decorator** (DESIGN wording). Invisible to adapters. But a `ChatModel` sees only messages and
   options, not feature or prompt version, so it would have to key on the full prompt text. The adapter would also
   log and bill a replayed answer as if it were a live call.
2. **Explicit `AiopsFacade.findReplay` call in each adapter** before the budget gate. Most transparent, but every
   adapter repeats the hit/miss/fallback branching.
3. **Spring AI `CallAdvisor`** in the `ChatClient` chain, which can return an answer without calling the model
   (middleware with short-circuit). The adapter passes the key parts explicitly as advisor params. Reusable across
   adapters, and the adapter learns from a response marker that the answer was replayed.

**L7 key and normalisation**
1. Exact bytes: never a false hit, but a CRLF or trailing space is a miss (and `on-miss: fail` then breaks CI).
2. **Light**: Unicode NFC, CRLF to LF, whitespace runs to one space, trimmed. Case and punctuation kept.
3. Light + a hash of the call options and schema: stricter, more re-recording.

**Hits in the generation log**
1. **Not logged**: the log holds real provider calls only, so cost and audit stay truthful.
2. Logged with zero cost and a `replayed_from` reference: fuller audit per subject, but the log grows in CI and
   demo, and every cost query must filter replays.

**Recordings**
1. **One live Haiku run on synthetic items** (under 0.01 USD), WireMock in proxy-record mode for raw HTTP bodies.
2. Ollama only: free, but keyed to the wrong model, and the Anthropic fixtures stay hand-written.
3. Defer: build the mechanism, record later.

## Decision

- **Advisor (option 3).** `ReplayAdvisor` (package-private, `aiops/infrastructure/ai`) runs at
  `LOWEST_PRECEDENCE - 100`, just before Spring AI's `ChatModelCallAdvisor`, so it replaces exactly the provider
  round trip. Adapters add `aiops.replay(new ReplayRequest(feature, model, promptVersion, input))` to the call's
  advisors. A hit returns the recorded text with response metadata `briefwise.replayed=true` and no usage;
  `AiopsFacade.isReplayed(response)` tells the adapter to skip `recordGeneration` and `checkAndRecordUsage`. A miss
  with `on-miss: fail` throws `ReplayMissException` (shared kernel); with `fallback` it calls the model. Without
  `briefwise.llm.provider=replay` the advisor only passes the call on. `findReplay` stays on the facade as the plain
  lookup.
- **L7: light normalisation (option 2).** Key = SHA-256 over feature, model, prompt version and the normalised data
  block, with a unit separator between parts. The system prompt is not hashed: the prompt version stands for it,
  and `NewsClassificationPromptTest` enforces a version bump on any prompt change. The model is the configured name
  (`spring.ai.<provider>.chat.model`), not the dated ID the provider echoes back. The recording adapter passes the
  same `ReplayRequest` to `GenerationEntry.replay`, so record and lookup hash the same parts.
- **Hits are not logged (option 1).** An INFO line carries the key; the original recorded row is the audit trail.
- **Recordings: live Haiku on synthetic items (option 1).** Manual, documented in `evals/replay/README.md`, never in a
  test.
- Defaults that follow:
  - Only usable answers get a `replay_key` (V13; malformed and fallback rows get none, enforced in
    `NewGeneration`), so a replay can never loop through re-asks.
  - An unusable replayed answer (the parser changed under an old recording) fails the item at once, with a warning
    to re-record.
  - A miss under `fail` defers the item (`RECEIVED`), never `UNCLASSIFIED`.
  - Stores are asked in order: committed fixtures (`evals/replay/*.jsonl`, loaded at startup; a duplicate key or
    malformed line fails startup), then the generation log (newest row per key).
  - Profiles: `test` and `demo` use `replay` + `fail`. Demo points Anthropic at a dummy key and the discard port, so
    it cannot reach the API. `local` stays `ollama` (`replay` + `fallback` is a one-line switch). `cloud`/`job`
    stay `anthropic`.

## Consequences

- CI makes zero LLM HTTP calls: `ReplayNewsClassificationIT` asserts it. `LlmNewsClassifierIT` runs with
  `on-miss: fallback`, so it still exercises HTTP through WireMock and also covers record-then-replay.
- `AiopsFacade` now exposes a Spring AI type (`ChatClient.AdvisorSpec`). Acceptable: every LLM adapter already
  depends on Spring AI.
- Call options are not in the key: changing effort or `maxTokens` without a prompt version bump keeps old answers.
  Changing `ClassificationAnswer` without a bump shows up as unusable replays (warning + `UNCLASSIFIED`). Revisit
  (option 3) if that bites.
- Replay proves wiring and determinism, not model quality. Quality stays with the slice 8 eval.
- The demo still has no seeded news. Seeding it from `synthetic_news.sql` is part of the M6 demo Compose profile.
- The recordings require one manual run with your API key. Until then `evals/replay/` holds no `*.jsonl`, and the
  WireMock `classification-valid.json` stays hand-written.
