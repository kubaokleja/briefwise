-- Context: market. Instruments and daily closes. Returns are derived on read, not stored.
-- Adjusted close / OHLC are deliberately left out until the price source is chosen (Stooq vs yfinance);
-- add them then with a new migration.

CREATE TABLE instruments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker          VARCHAR(16) NOT NULL UNIQUE,   -- serves lookup by ticker
    name            VARCHAR(200) NOT NULL,
    instrument_type VARCHAR(8) NOT NULL CHECK (instrument_type IN ('STOCK', 'ETF')),
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE daily_prices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id UUID NOT NULL REFERENCES instruments (id),
    trade_date    DATE NOT NULL,
    close         NUMERIC(18, 6) NOT NULL CHECK (close > 0),
    volume        BIGINT NOT NULL DEFAULT 0 CHECK (volume >= 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (instrument_id, trade_date)   -- serves price history by date range and "latest N closes" (getDailyMove)
);
