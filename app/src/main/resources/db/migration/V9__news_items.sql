-- Context: news. Ingested articles (DESIGN 7.1). Classification columns come with M1 slice 4.
-- Finnhub licence: personal use, no redistribution; store headline, short summary and link only, never full text.

CREATE TABLE news_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    headline        TEXT NOT NULL,
    summary         TEXT NOT NULL DEFAULT '',
    url             TEXT NOT NULL,
    url_hash        VARCHAR(64) NOT NULL UNIQUE,    -- SHA-256 of the normalised URL; serves exact-duplicate lookup
    source          VARCHAR(100) NOT NULL,         -- publisher as reported by the provider (e.g. Reuters)
    published_at    TIMESTAMPTZ NOT NULL,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('RECEIVED', 'CLASSIFIED', 'UNCLASSIFIED', 'SKIPPED')),
    duplicate_of_id UUID REFERENCES news_items (id), -- near-duplicate headline (ADR 0009); points to the original
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (duplicate_of_id IS NULL OR status = 'SKIPPED')
);

-- Serves the near-duplicate search: same ticker, published_at within the dedup window.
CREATE INDEX news_items_published_at_idx ON news_items (published_at);

-- One article can concern several held tickers (same URL fetched for AAPL and MSFT).
-- Tickers are plain symbols, no FK to instruments (as for positions).
CREATE TABLE news_item_tickers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    news_item_id UUID NOT NULL REFERENCES news_items (id) ON DELETE CASCADE,
    ticker       VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (news_item_id, ticker)                  -- idempotent "add ticker"; serves loading an item's tickers
);

-- Serves the near-duplicate search (candidates per ticker) and, later, news per held ticker.
CREATE INDEX news_item_tickers_ticker_idx ON news_item_tickers (ticker);
