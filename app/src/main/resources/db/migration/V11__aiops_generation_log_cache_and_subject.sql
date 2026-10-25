-- Context: aiops. Prompt caching token counts (ADR 0012) and the business object a call was made for (ADR 0014).
-- prompt_tokens stays the uncached input; the cached prefix is counted in the two cache columns, as Anthropic reports.

ALTER TABLE ai_generation_log
    ADD COLUMN cache_write_tokens INTEGER NOT NULL DEFAULT 0 CHECK (cache_write_tokens >= 0),
    ADD COLUMN cache_read_tokens  INTEGER NOT NULL DEFAULT 0 CHECK (cache_read_tokens >= 0),
    ADD COLUMN subject_id         UUID;   -- e.g. the news item for news-classification; the feature says the kind

-- Serves "which calls were made for this object?" (audit).
CREATE INDEX ai_generation_log_subject_id_idx ON ai_generation_log (subject_id) WHERE subject_id IS NOT NULL;
