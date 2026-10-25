-- Context: news. Classification result per item (M1 slice 4, ADR 0012/0014): one label set per item, plus which of
-- the item's tickers it affects. A classified item has all label columns; an unclassified one has a reason.

ALTER TABLE news_items
    ADD COLUMN sentiment           VARCHAR(16) CHECK (sentiment IN ('NEGATIVE', 'NEUTRAL', 'POSITIVE')),
    ADD COLUMN relevance           VARCHAR(16) CHECK (relevance IN ('LOW', 'MEDIUM', 'HIGH')),
    ADD COLUMN event_type          VARCHAR(16) CHECK (event_type IN ('EARNINGS', 'GUIDANCE', 'M_AND_A', 'REGULATORY',
                                       'LITIGATION', 'MANAGEMENT', 'MACRO', 'PRODUCT', 'ANALYST', 'OTHER')),
    ADD COLUMN rationale           TEXT,
    ADD COLUMN prompt_version      VARCHAR(32),    -- results of different prompt versions are never mixed up
    ADD COLUMN unclassified_reason VARCHAR(32) CHECK (unclassified_reason IN ('INVALID_OUTPUT', 'PROVIDER_ERROR',
                                       'INJECTION_SUSPECTED')),
    ADD COLUMN classified_at       TIMESTAMPTZ,    -- when the item left RECEIVED for CLASSIFIED or UNCLASSIFIED
    ADD CONSTRAINT news_items_classified_has_labels CHECK (status <> 'CLASSIFIED' OR (sentiment IS NOT NULL
        AND relevance IS NOT NULL AND event_type IS NOT NULL AND rationale IS NOT NULL AND prompt_version IS NOT NULL)),
    ADD CONSTRAINT news_items_unclassified_has_reason CHECK ((status = 'UNCLASSIFIED') = (unclassified_reason IS NOT NULL));

-- The classifier names the tickers an item actually affects (a subset of the item's tickers).
ALTER TABLE news_item_tickers
    ADD COLUMN affected BOOLEAN NOT NULL DEFAULT false;

-- No index on the label columns yet: the first query by relevance comes with getRelevantNews (slice 7).
