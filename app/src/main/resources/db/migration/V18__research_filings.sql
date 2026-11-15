-- Context: research. SEC filings of held companies and their extracted sections (M2 slice 2, ADR 0025, ADR 0026).
-- Provenance for citations: accession, source URL and content hash. Chunks and embeddings come in slice 3 (V19).

-- One row per filing. Re-ingest compares content_sha256: same hash = skip, new hash = replace sections, keep id.
-- ticker and cik have no FK to instruments: contexts reference each other by value through facades.
CREATE TABLE research_filings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accession_no      VARCHAR(20) NOT NULL UNIQUE CHECK (accession_no ~ '^[0-9]{10}-[0-9]{2}-[0-9]{6}$'),
                                                    -- serves the re-ingest lookup (findContentSha256)
    ticker            VARCHAR(16) NOT NULL,
    cik               VARCHAR(10) NOT NULL CHECK (cik ~ '^[0-9]{10}$'),
    form              VARCHAR(8) NOT NULL CHECK (form IN ('10-K', '10-Q', '8-K')),  -- amendments are never stored
    filed_at          DATE NOT NULL,
    period_of_report  DATE,                         -- EDGAR reportDate, if given
    primary_document  TEXT NOT NULL,
    items             TEXT[] NOT NULL DEFAULT '{}', -- 8-K item numbers as listed by EDGAR; empty for 10-K / 10-Q
    source_url        TEXT NOT NULL,                -- the primary document on sec.gov, cited later
    content_sha256    VARCHAR(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    extractor_version VARCHAR(16) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Serves "filings of a ticker, newest first" (research Q&A, M2 slice 7).
CREATE INDEX research_filings_ticker_idx ON research_filings (ticker, filed_at DESC);

-- One row per kept item. The id is also the content id given to the injection screen.
-- A suspected section is stored (audit) but never chunked or retrieved.
CREATE TABLE research_filing_sections (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filing_id           UUID NOT NULL REFERENCES research_filings (id) ON DELETE CASCADE,
    ordinal             INT NOT NULL CHECK (ordinal >= 0),   -- order in the filing; part of the content hash
    item_id             VARCHAR(16) NOT NULL,                -- 1A, II-1A, 2.02, EX-99.1 (SectionPolicy)
    title               TEXT NOT NULL,
    text                TEXT NOT NULL CHECK (length(btrim(text)) > 0),
    injection_suspected BOOLEAN NOT NULL,
    matched_rules       TEXT[] NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (filing_id, ordinal),                             -- serves loading a filing's sections in order
    UNIQUE (filing_id, item_id),                             -- one section per item and filing
    CHECK (injection_suspected = (cardinality(matched_rules) > 0))
);
