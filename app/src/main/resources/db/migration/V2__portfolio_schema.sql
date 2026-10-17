-- Context: portfolio. Synthetic clients only: `code` is a made-up identifier, never a real name.
-- positions.ticker is a plain symbol, not a foreign key into the market context.

CREATE TABLE clients (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(32) NOT NULL UNIQUE,
    risk_profile VARCHAR(16) NOT NULL CHECK (risk_profile IN ('CONSERVATIVE', 'BALANCED', 'GROWTH')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE portfolios (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     UUID NOT NULL REFERENCES clients (id),
    name          VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE positions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios (id),
    ticker       VARCHAR(16) NOT NULL,
    quantity     NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
    weight       NUMERIC(9, 6) NOT NULL CHECK (weight > 0 AND weight <= 1),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (portfolio_id, ticker)
);
-- The UNIQUE (portfolio_id, ticker) index also serves "holdings of a portfolio" (getHoldings, getPortfolio).
