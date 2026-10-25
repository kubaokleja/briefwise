# Anthropic Messages API fixtures

WireMock answers for `LlmNewsClassifierIT`. Hand-written in the documented Messages API response format
(2026-10-24), not captured from a live call: tests never call a live LLM. Replace them with responses recorded from
`ai_generation_log` once the first real classification has run (slice 5 exports replay fixtures from the log).

- `classification-valid.json`: thinking block + JSON text; enum in mixed case (structured outputs do not guarantee
  casing); cache read tokens set.
- `classification-malformed.json`: truncated at `max_tokens`, cache write tokens set.
- `classification-unknown-ticker.json`: valid JSON naming a ticker the item does not have.
