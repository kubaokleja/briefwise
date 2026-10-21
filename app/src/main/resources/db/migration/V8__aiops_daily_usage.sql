-- Context: aiops. Running totals per feature and UTC day for the daily budget gate (ADR 0008).
-- Updated with an atomic upsert after every call, so concurrent callers never lose an increment.

CREATE TABLE ai_daily_usage (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usage_date        DATE NOT NULL,                    -- UTC date
    feature           VARCHAR(64) NOT NULL,
    call_count        INTEGER NOT NULL DEFAULT 0 CHECK (call_count >= 0),
    prompt_tokens     BIGINT NOT NULL DEFAULT 0 CHECK (prompt_tokens >= 0),
    completion_tokens BIGINT NOT NULL DEFAULT 0 CHECK (completion_tokens >= 0),
    cost_usd          NUMERIC(14, 8) NOT NULL DEFAULT 0 CHECK (cost_usd >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usage_date, feature)   -- serves the upsert, "spent today by feature" and the cost summary by date range
);
