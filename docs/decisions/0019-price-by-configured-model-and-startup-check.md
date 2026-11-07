# 0019. Price LLM calls by the configured model; check pricing and budgets at startup

- Status: Accepted
- Date: 2026-11-07

## Context

Found in the news classification walkthrough (2026-11-07, PLANNING slice 5): the LLM adapters priced each call by
the model name the provider echoes back (`ChatResponse.getMetadata().getModel()`), and `PriceList.priceOf` matches
exactly ([ADR 0008](0008-llm-daily-budget-gate.md): an unpriced model must not be logged as 0 USD). If Anthropic
answers with a dated snapshot ID instead of the configured alias `claude-haiku-5-5`, `UnpricedModelException` is
thrown *after* the call was billed. In the classifier it escapes `NewsFacade.processIngested`, so Kafka redelivers
3 times, the message goes to the DLT, and the sweep retries it 3 more times: up to ~6 paid calls per item with
nothing stored. `NoBudgetConfiguredException` from `checkAndRecordUsage` has the same path. Both break the "does
not throw" contract of the classifier port ([ADR 0012](0012-news-classification-llm-call.md) R1). The summarizer
and the eval batch classifier had the same shape (the batch classifier already priced by the configured name).

`ResourceNotFoundException` from `FacadeHeldInstruments` was named in the same finding. It is thrown in the code
gate *before* any call, so it costs nothing (a free retry loop). It is left to the M3 "one policy for an unknown held
instrument" question (Domain Questions Log 2026-11-07).

## Options

1. **Price by the configured model name.** It cannot fail after a call once the name is known to be priced. The
   log no longer shows which exact version answered, unless that is stored separately.
2. **Catch the exceptions in the adapter** and turn the item into `UNCLASSIFIED`. Keeps the port contract, but the
   cost of the call is still not computed, and a missing price is only found once money has been spent.
3. **Both, plus a startup check.** Price by the configured name, keep the echoed ID in its own column for the audit,
   check at startup that every adapter's feature has a budget and its model a price, and never throw from recording
   after a paid call.

## Decision

Option 3 (owner's choice, 2026-11-07).

- `ai_generation_log.model` is the configured model name; it prices the call and feeds the daily budget. The model
  ID the provider reports goes into the new nullable `response_model` (Flyway V16), for the audit only.
  `AiopsFacade.GenerationEntry` gains `responseModel`.
- `AiopsFacade.ensureConfigured(feature, model)` (port `UsageBudget.requireConfigured`): every LLM adapter calls it
  in its constructor. A missing budget or price stops the app at startup (fail closed, as ADR 0008), so it is found
  in CI and before the first scheduled run, never after a billed call.
- After a paid call, the adapter records generation and usage inside a `try`; a failure is logged at `ERROR` with
  the token counts and not thrown. Throwing cannot undo the spend and only triggers paid retries.
- The replay key keeps the configured model name (unchanged, ADR 0015).

## Consequences

- An unexpected model ID in the response is harmless: cost is right, the ID is visible in `response_model`.
- If the provider served a different, dearer model than the one configured (not expected for an alias), the cost
  would be under-reported. `response_model` makes that visible; compare it after the recording run.
- A swallowed recording failure leaves a paid call outside the log and the budget. With the startup check this
  only happens when the database fails, and then the next `ensureBudgetAvailable` fails too, before any call.
- Every new LLM adapter must call `ensureConfigured` (added to the `llm-adapter` skill).
- Revisit if a feature ever picks its model per call (e.g. routing between Haiku and Sonnet): the check then has to
  cover every model the feature may use.
