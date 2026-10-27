# Anthropic Messages API fixtures

WireMock answers for `LlmNewsClassifierIT`. Hand-written in the documented Messages API response format
(2026-10-24), not captured from a live call: tests never call a live LLM. `classification-valid.json` gets replaced by a
raw response body captured once through a WireMock recording proxy (`evals/replay/README.md`, step 7); the
malformed and unknown-ticker answers stay hand-written, because a live model cannot be made to produce them reliably.

- `classification-valid.json`: thinking block + JSON text; enum in mixed case (structured outputs do not guarantee
  casing); cache read tokens set.
- `classification-malformed.json`: truncated at `max_tokens`, cache write tokens set.
- `classification-unknown-ticker.json`: valid JSON naming a ticker the item does not have.
