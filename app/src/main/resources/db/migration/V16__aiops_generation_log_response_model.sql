-- Context: aiops. The model ID the provider reports back (e.g. a dated snapshot of the configured alias), kept for the
-- audit trail (ADR 0019). Cost and budget use `model`, the configured name, which is validated against the price list
-- at startup, so a call is never billed under a name that cannot be priced. NULL when the call failed or the provider
-- reported no model.

ALTER TABLE ai_generation_log
    ADD COLUMN response_model VARCHAR(128);
