-- Synthetic news items for recording replay fixtures (M1 slice 5, ADR 0015). Written by hand for this project: no
-- Finnhub text, so the recordings may be committed (Finnhub licence, PLANNING Domain Questions 2026-10-21).
-- Dev seed only, never a Flyway migration. Idempotent: fixed ids, ON CONFLICT DO NOTHING. Tickers are held by the
-- seeded portfolios (V5), so the code gate lets every item through. Coverage: each sentiment, nine event types, one
-- multi-ticker item. url_hash here is a plain SHA-256 of the URL (the app normalises first); fine for a seed.
--
-- Run (local Docker Compose):  docker compose exec -T postgres psql -U briefwise -d briefwise < evals/replay/synthetic_news.sql
-- The outbox rows go to news.raw through the relay; the consumer classifies them (see README.md).

BEGIN;

CREATE TEMP TABLE synthetic_news (n INT, ticker TEXT, extra_ticker TEXT, headline TEXT, summary TEXT) ON COMMIT DROP;

INSERT INTO synthetic_news VALUES
 (1, 'AAPL', NULL, 'Apple quarterly revenue tops estimates on services growth',
     'Revenue rose 9% year on year, ahead of the consensus estimate, driven by subscriptions and the app store.'),
 (2, 'MSFT', NULL, 'Microsoft trims full-year outlook as cloud spending slows',
     'The company lowered its annual revenue guidance, citing longer sales cycles among enterprise customers.'),
 (3, 'NVDA', NULL, 'Broker downgrades Nvidia to neutral and cuts price target',
     'The analyst cited stretched valuation and moved the rating from buy to neutral.'),
 (4, 'JPM', NULL, 'JPMorgan agrees to pay regulator fine over record-keeping lapses',
     'The bank settled with a market regulator over employees using unapproved messaging apps.'),
 (5, 'KO', NULL, 'Coca-Cola names new chief executive effective next quarter',
     'The board appointed the current chief operating officer to succeed the retiring CEO.'),
 (6, 'XOM', NULL, 'Exxon Mobil to acquire shale producer in all-stock deal',
     'The transaction would add acreage in the Permian Basin and is expected to close next year.'),
 (7, 'JNJ', NULL, 'Johnson & Johnson faces new lawsuits over consumer product',
     'Plaintiffs filed additional claims; the company said it would defend itself vigorously.'),
 (8, 'PG', NULL, 'Procter & Gamble launches refillable packaging line in Europe',
     'The new line covers three household brands and starts in five countries.'),
 (9, 'AMZN', 'MSFT', 'Amazon and Microsoft cut cloud storage prices for business customers',
     'Both providers announced lower list prices for archival storage tiers starting next month.'),
 (10, 'JPM', NULL, 'Central bank holds interest rates steady, signals patience',
     'Policymakers left the benchmark rate unchanged and said future moves depend on incoming data.');

INSERT INTO news_items (id, headline, summary, url, url_hash, source, published_at, status)
SELECT ('a0000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid, headline, summary,
       'https://example.com/synthetic/' || n, encode(sha256(('https://example.com/synthetic/' || n)::bytea), 'hex'),
       'Synthetic', TIMESTAMPTZ '2026-10-27 06:00:00+00' + n * INTERVAL '5 minutes', 'RECEIVED'
FROM synthetic_news
ON CONFLICT DO NOTHING;

INSERT INTO news_item_tickers (news_item_id, ticker)
SELECT ('a0000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid, t
FROM synthetic_news, LATERAL (VALUES (ticker), (extra_ticker)) AS tickers (t)
WHERE t IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO news_outbox (id, aggregate_id, topic, message_key, payload)
SELECT ('b0000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       ('a0000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid, 'news.raw', ticker,
       jsonb_build_object('messageId', 'b0000000-0000-4000-8000-' || lpad(n::text, 12, '0'),
                          'newsId', 'a0000000-0000-4000-8000-' || lpad(n::text, 12, '0'),
                          'occurredAt', '2026-10-27T06:00:00Z')
FROM synthetic_news
ON CONFLICT DO NOTHING;

COMMIT;
