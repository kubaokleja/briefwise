-- Context: market. SEC issuer id (CIK) per stock, so research can fetch filings from EDGAR (ADR 0026).
-- Stored as 10 digits, zero-padded (the form EDGAR's submissions API uses). NULL for ETFs: no 10-K / 10-Q / 8-K.
-- Values from https://www.sec.gov/files/company_tickers.json, checked 2026-11-14.
-- XOM maps to 2115436 (ExxonMobil Holdings Corp); the former Exxon Mobil Corp CIK 0000034088 may still hold older
-- filings (to verify at the first ingest).

ALTER TABLE instruments
    ADD COLUMN cik VARCHAR(10) UNIQUE CHECK (cik ~ '^[0-9]{10}$');

UPDATE instruments i
SET cik = v.cik, updated_at = now()
FROM (VALUES
    ('AAPL',  '0000320193'),
    ('MSFT',  '0000789019'),
    ('NVDA',  '0001045810'),
    ('AMZN',  '0001018724'),
    ('JPM',   '0000019617'),
    ('JNJ',   '0000200406'),
    ('PG',    '0000080424'),
    ('KO',    '0000021344'),
    ('XOM',   '0002115436'),
    ('GOOGL', '0001652044'),
    ('META',  '0001326801'),
    ('TSLA',  '0001318605'),
    ('BAC',   '0000070858'),
    ('GS',    '0000886982'),
    ('UNH',   '0000731766'),
    ('LLY',   '0000059478'),
    ('MRK',   '0000310158'),
    ('PFE',   '0000078003'),
    ('WMT',   '0000104169'),
    ('COST',  '0000909832'),
    ('HD',    '0000354950'),
    ('PEP',   '0000077476'),
    ('DIS',   '0001744489'),
    ('CVX',   '0000093410'),
    ('CAT',   '0000018230'),
    ('BA',    '0000012927'),
    ('CSCO',  '0000858877'),
    ('ORCL',  '0001341439'),
    ('INTC',  '0000050863'),
    ('V',     '0001403161')
) AS v (ticker, cik)
WHERE i.ticker = v.ticker;
