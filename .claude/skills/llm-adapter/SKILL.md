---
name: llm-adapter
description: Use when adding or changing a call to an LLM in the Briefwise app (e.g. "summarise the portfolio news", "draft the brief section", "add a model call to research"). Covers the prompt files and version guard, the code gate, the budget gate, native structured output into a record, re-ask and fallback, the generation log, the replay advisor, and the WireMock + replay tests. Pattern first implemented by hand for news classification (ADR 0012, 0015).
---

# LLM adapter

Reference implementation: `news/infrastructure/ai/LlmNewsClassifier` + `NewsClassificationPrompt`,
`NewsClassificationConfig`, `NewsClassificationProperties`, `ClassificationAnswer`
([ADR 0012](../../../docs/decisions/0012-news-classification-llm-call.md),
[0014](../../../docs/decisions/0014-classification-output-shape.md),
[0015](../../../docs/decisions/0015-replay-provider.md)). Copy its shape; the rules themselves are in CLAUDE.md
(LLM calls, Cost rules) and are not repeated here.

## Decide first (name each as an AI decision point, I decide, ADR)

1. **Model** and why (cost per call x expected calls per day vs quality). The name goes in `application.yml` only.
2. **Effort / temperature**: models without temperature (Haiku 5.5) use `effort`.
3. **`maxTokens`**: thinking tokens count toward it.
4. **Output shape**: the record the model fills in (enums, required fields, what is validated in code).
5. **Few-shot**: none, static (never from a golden set), or dynamic.
6. **Caching**: is the stable prefix above the model's minimum cacheable length?
7. **Code gate**: when is the call skipped without asking the model? (news: still `RECEIVED`, a ticker still held.)
8. **Fallback** when no valid answer arrives (news: `UNCLASSIFIED` + reason). Text features: template +
   `isFallback`, compliance rules (CLAUDE.md standing instruction 8).
9. **Budget**: a `briefwise.aiops.daily-budget-usd.<feature>` value.

## Steps

1. **Prompt files** in `prompts/<feature>/`: `system.md` (stable prefix: instructions, label definitions, examples),
   `user.md` (data block with placeholders; untrusted text inside a delimiting tag), `prompt.properties`
   (`version` + `sha256`). Add a `*PromptTest` that fails when the hash does not match (copy
   `NewsClassificationPromptTest`). Render placeholders in one pass.
2. **Answer record** in `infrastructure/ai` with `@JsonPropertyDescription`s. Map it to the domain record with
   validation in a method (`toClassification`); invalid content throws, and that counts as invalid output.
3. **Properties** record `briefwise.<context>.<feature>` (feature name, `max-tokens`, `effort`, `max-attempts`).
   Per-call options supplier in a config class: provider-specific options only there.
4. **Adapter**, package-private, `@ConditionalOnExpression(CHAT_MODEL_CONFIGURED)`, plus a no-model variant that
   defers. Per attempt, in this order:
   1. `aiops.ensureBudgetAvailable(feature)`. On `UsageLimitExceededException`: defer.
   2. `chatClient.prompt().system(...).user(data).options(...)` with the native structured output advisor params
      (`STRUCTURED_OUTPUT_NATIVE`, `STRUCTURED_OUTPUT_SCHEMA`, `OUTPUT_FORMAT` from a `BeanOutputConverter`) **and**
      `.advisors(aiops.replay(new ReplayRequest(feature, configuredModel, promptVersion, data)))`.
   3. Catch `ReplayMissException` first: defer, never fall back. Any other `RuntimeException` is a provider error:
      fall back at once (the SDK already retried).
   4. `aiops.isReplayed(response)`: parse as usual, but no log, no usage, no re-ask (an unusable replay fails with a
      warning).
   5. Live answer: parse yourself (`converter.convert`), not `.entity()`, so malformed answers are logged too.
      Invalid: re-ask up to `max-attempts`.
   6. Every live attempt: `recordGeneration` (prompt = system + separator + data, response, tokens incl. cache,
      latency, `fallback` on the give-up attempt, `subjectId`, and `replay` = the same `ReplayRequest` **only for a
      usable answer**), then `checkAndRecordUsage`.
5. **Screening**: untrusted text goes through `ComplianceFacade.screenUntrustedContent` in the application service,
   before the adapter is called.
6. **Pricing** for the model in `briefwise.aiops.pricing` (otherwise `UnpricedModelException`).

## Tests

- `*PromptTest` (hash guard, rendering, placeholder injection), answer record test (schema + mapping) as plain unit
  tests.
- `<Adapter>IT` against WireMock with `@TestPropertySource(properties = "briefwise.llm.replay.on-miss=fallback")`,
  emptying `ai_generation_log` before each test: request shape (model, `max_tokens`, effort, schema, cache control,
  data block), valid, malformed then re-ask, malformed twice, invalid content, timeout, 5xx/529, budget, and
  record-then-replay (second identical input: no HTTP request). Fixtures in `src/test/resources/anthropic/`: raw
  response bodies, recorded where possible (`evals/replay/README.md`).
- Replay IT on the plain `test` profile (`replay` + `fail`): hit, zero HTTP requests, no log row, no usage; miss
  defers. Copy `ReplayNewsClassificationIT`.
- Application service: Mockito on the classifier/generator port (code gate, fallback mapping).
- Never a live call in a test. Recording is a manual step (`evals/replay/README.md`).

## Afterwards

- Record fixtures for the new feature (synthetic input only), export to `evals/replay/<feature>.jsonl`.
- ADR for the decisions above. Tick PLANNING. Do not commit.
