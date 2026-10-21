-- Context: aiops. One row per LLM call (DESIGN 12.1): what the model saw and said, what it cost.
-- Later the replay provider (M1 slice 5) looks up recorded responses here; its lookup key and index come then.
-- Retention (DESIGN 13.6: full text for 30 days, then hash + metrics only) is not implemented yet.

CREATE TABLE ai_generation_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature           VARCHAR(64) NOT NULL,             -- e.g. news-classification; budgets are per feature
    model             VARCHAR(128) NOT NULL,
    prompt_version    VARCHAR(32) NOT NULL,
    prompt            TEXT NOT NULL,
    response          TEXT,                             -- NULL when the call failed (timeout, provider error)
    prompt_tokens     INTEGER NOT NULL CHECK (prompt_tokens >= 0),
    completion_tokens INTEGER NOT NULL CHECK (completion_tokens >= 0),
    cost_usd          NUMERIC(14, 8) NOT NULL CHECK (cost_usd >= 0),
    latency_ms        BIGINT NOT NULL CHECK (latency_ms >= 0),
    correlation_id    VARCHAR(64),
    is_fallback       BOOLEAN NOT NULL DEFAULT false,   -- the caller used its fallback (e.g. UNCLASSIFIED)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
