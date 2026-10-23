-- Candidates for the stratified "company" sample (M1 slice 2). The random export (export_headlines.sql) is mostly
-- passing mentions: Finnhub tags tickers loosely, so it holds almost no HIGH-relevance or non-OTHER items. Headlines
-- that name the held company are a proxy for company-specific news and fill the rare classes.
--
-- Output: every original whose headline names its company, with a seeded per-ticker rank. append_company_sample.py
-- drops ids that are already labelled, takes the top N per ticker and appends them to headlines_to_label.csv.

SELECT setseed(0.42);

WITH alias(ticker, pattern) AS (VALUES
    ('AAPL', '\m(Apple|AAPL)\M'),
    ('MSFT', '\m(Microsoft|MSFT|Azure)\M'),
    ('AMZN', '\m(Amazon|AMZN|AWS)\M'),
    ('NVDA', '\m(Nvidia|NVDA)\M'),
    ('JPM',  '\m(JPMorgan|JPM|Dimon)\M'),
    ('JNJ',  '(Johnson & Johnson|\mJ&J\M|\mJNJ\M)'),
    ('PG',   '(Procter|\mP&G\M|\mPG\M)'),
    ('KO',   '(Coca-Cola|\mCoke\M|\mKO\M)'),
    ('XOM',  '\m(Exxon|ExxonMobil|XOM)\M')
),
matched AS (
    -- one stratum per item: the first held ticker its headline names
    SELECT DISTINCT ON (n.id) n.id, t.ticker AS stratum
    FROM news_items n
    JOIN news_item_tickers t ON t.news_item_id = n.id
    JOIN alias a ON a.ticker = t.ticker
    WHERE n.status = 'RECEIVED' AND n.duplicate_of_id IS NULL AND n.headline ~* a.pattern
    ORDER BY n.id, t.ticker
),
ranked AS (
    SELECT m.*, row_number() OVER (PARTITION BY m.stratum ORDER BY random()) AS rank
    FROM (SELECT * FROM matched ORDER BY id) m
)
SELECT r.stratum, r.rank, n.id,
       (SELECT string_agg(t.ticker, ' ' ORDER BY t.ticker) FROM news_item_tickers t WHERE t.news_item_id = n.id) AS tickers,
       n.published_at, n.source, n.headline, n.summary, n.url
FROM ranked r
JOIN news_items n ON n.id = r.id
ORDER BY r.stratum, r.rank;
