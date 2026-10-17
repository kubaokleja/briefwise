-- Reference data: the instruments held by the synthetic portfolios (V5). Prices are loaded later by the ETL.
INSERT INTO instruments (ticker, name, instrument_type) VALUES
    ('AAPL', 'Apple Inc.',                          'STOCK'),
    ('MSFT', 'Microsoft Corporation',               'STOCK'),
    ('NVDA', 'NVIDIA Corporation',                  'STOCK'),
    ('AMZN', 'Amazon.com, Inc.',                    'STOCK'),
    ('JPM',  'JPMorgan Chase & Co.',                'STOCK'),
    ('JNJ',  'Johnson & Johnson',                   'STOCK'),
    ('PG',   'The Procter & Gamble Company',        'STOCK'),
    ('KO',   'The Coca-Cola Company',               'STOCK'),
    ('XOM',  'Exxon Mobil Corporation',             'STOCK'),
    ('SPY',  'SPDR S&P 500 ETF Trust',              'ETF'),
    ('AGG',  'iShares Core U.S. Aggregate Bond ETF', 'ETF');
