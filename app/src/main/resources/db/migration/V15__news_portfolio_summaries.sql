-- Context: news. Daily news summary per portfolio (M1 slice 7, ADR 0017) and the indexes of the news selection.

-- One summary per portfolio and UTC date, replaced in place when the regeneration gate allows a new one.
-- portfolio_id has no FK: contexts reference each other by id through facades (as tickers do for instruments).
CREATE TABLE news_portfolio_summaries (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id   UUID NOT NULL,
    summary_date   DATE NOT NULL,                   -- UTC
    generated_at   TIMESTAMPTZ NOT NULL,            -- the gate compares it with news_items.classified_at
    points         JSONB NOT NULL,                  -- [{text, citations: [{type, sourceId, locator, excerpt}]}]; [] = nothing relevant
    is_fallback    BOOLEAN NOT NULL,                -- template, no compliant model answer (shown with a banner)
    model          VARCHAR(100),                    -- NULL for a template or an empty summary
    prompt_version VARCHAR(32),
    disclaimer     TEXT NOT NULL,                   -- as shown, appended in code
    violations     JSONB NOT NULL DEFAULT '[]',     -- rules a template fallback still breaks: [{rule, offendingText}]
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (portfolio_id, summary_date),            -- upsert target; serves "latest summary of a portfolio"
    CHECK (NOT is_fallback OR model IS NULL),
    CHECK ((model IS NULL) = (prompt_version IS NULL))
);

-- Serves getRelevantNews: CLASSIFIED items of the wanted relevance published since the window start.
CREATE INDEX news_items_selection_idx ON news_items (status, relevance, published_at);

-- Serves getRelevantNews: items whose affected tickers include a held one.
CREATE INDEX news_item_tickers_affected_idx ON news_item_tickers (ticker, news_item_id) WHERE affected;
