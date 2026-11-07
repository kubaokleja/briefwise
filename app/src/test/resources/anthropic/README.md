# Anthropic Messages API fixtures

WireMock answers for `LlmNewsClassifierIT`. Tests never call a live LLM. One body is captured from the live API,
the rest are hand-written in the documented Messages API response format (2026-10-24): edge cases a live model
cannot be made to produce reliably (thinking block, mixed-case enum, truncation, unknown ticker).

- `classification-recorded.json`: raw body captured 2026-11-07 through the WireMock recording proxy
  (`evals/replay/README.md`, step 7) for synthetic item 1 (AAPL, prompt v1, `claude-haiku-5-5`). The first call of
  the run, so it carries cache *write* tokens. Pins the real response shape (fields the hand-written bodies lack).
  Only the body was copied; the mapping files with the response headers stay outside the repo.

- `classification-valid.json`: thinking block + JSON text; enum in mixed case (structured outputs do not guarantee
  casing); cache read tokens set.
- `classification-malformed.json`: truncated at `max_tokens`, cache write tokens set.
- `classification-unknown-ticker.json`: valid JSON naming a ticker the item does not have.

Summary fixtures for `LlmNewsSummarizerIT` (ADR 0017), hand-written the same way; they cite the fixed test item id
`00000000-0000-4000-8000-0000000000a1`:

- `summary-valid.json`: thinking block + one point citing the test item.
- `summary-malformed.json`: truncated at `max_tokens`.
- `summary-unknown-id.json`: valid JSON citing an item that was not in the request.
