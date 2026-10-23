-- Export ~200 ingested headlines for hand labelling (M1 slice 2 -> golden set in slice 8). Run against the local
-- Docker Compose database after an ingest with a long lookback (see README.md next to this file).
--
-- Only originals (status RECEIVED, no near-duplicates), spread evenly over tickers. A fixed seed makes the split
-- reproducible: 150 rows "golden" (eval only) and 50 rows "fewshot" (pool for prompt examples). Never move a row
-- between the two after labelling, or the eval leaks into the prompt.
--
-- Label columns are empty on purpose: fill them in before any model has classified these items.

SELECT setseed(0.42);

WITH originals AS (
    SELECT n.id, n.published_at, n.headline, n.summary, n.url, n.source,
           string_agg(t.ticker, ' ' ORDER BY t.ticker) AS tickers,
           min(t.ticker) AS first_ticker
    FROM news_items n
    JOIN news_item_tickers t ON t.news_item_id = n.id
    WHERE n.status = 'RECEIVED' AND n.duplicate_of_id IS NULL
    GROUP BY n.id
),
spread AS (
    -- round-robin over tickers: the k-th item of every ticker before the (k+1)-th of any
    SELECT o.*, row_number() OVER (PARTITION BY first_ticker ORDER BY random()) AS per_ticker_rank, random() AS r
    FROM originals o
),
sample AS (
    SELECT * FROM spread ORDER BY per_ticker_rank, r LIMIT 200
)
SELECT CASE WHEN row_number() OVER (ORDER BY r) <= 150 THEN 'golden' ELSE 'fewshot' END AS split,
       id, tickers, published_at, source, headline, summary, url,
       '' AS sentiment,       -- NEGATIVE | NEUTRAL | POSITIVE
       '' AS relevance,       -- LOW | MEDIUM | HIGH
       '' AS event_type,      -- EARNINGS | GUIDANCE | M_AND_A | REGULATORY | LITIGATION | MANAGEMENT | MACRO | PRODUCT | OTHER
       '' AS duplicate_of_id, -- id of another row in this file that reports the same event (checks D1, ADR 0009)
       '' AS notes
FROM sample
ORDER BY split, tickers, published_at;
