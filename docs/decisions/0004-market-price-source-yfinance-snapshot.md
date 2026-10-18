# 0004. Market price source: yfinance refresh script + committed CSV snapshot

- Status: Accepted
- Date: 2026-10-18

## Context

`market` needs daily close and volume for ~30 US companies and a few ETFs, for the news-to-move check, the quant
batch ([DESIGN 5](../DESIGN.md#5-features-and-milestones)) and the demo. Budget <= 5 CHF/month and the demo
must work offline ([ADR 0003](0003-runtime-github-actions-and-neon.md)). Data is public market data, not client data.

Checked 2026-10-18: Stooq's CSV endpoint (`stooq.com/q/d/l/`) now answers with an HTML page that needs
JavaScript to pass a bot check, so a scripted download no longer works.

## Options

1. **Stooq CSV**: no key, simple format. Rejected: bot check blocks non-browser clients.
2. **yfinance (Python)**: free, rich (OHLC, adjusted close, splits). Unofficial scraper of Yahoo, can break or
   be rate-limited without notice; terms of use are for personal use.
3. **Alpha Vantage / Twelve Data free tier**: official API. Needs a key, tight daily/minute limits, adds a secret.
4. **Committed CSV snapshot only**: reproducible, offline, CI-safe. Goes stale; needs some way to refresh.

## Decision

Options 2 + 4 combined. A Python refresh script in `quant/` (yfinance) writes a CSV snapshot per ticker into the
repo (`data/prices/`). Flyway seed / a loader imports the snapshot into `daily_prices`; CI, the `demo` and `test`
profiles read only the snapshot and never touch the network. The script is run manually (and later by the
scheduled pipeline). Python writes only CSV; Java owns the schema and the import
([CLAUDE.md](../../CLAUDE.md): quant talks to Java through the database).

## Consequences

- No API key, no runtime dependency on Yahoo for tests or demo.
- If yfinance breaks, only the refresh step is affected; fall back to option 3 behind the same CSV format.
- Snapshot size: ~40 tickers x ~5 years x ~1260 rows is small (a few MB); revisit if it grows.
- Adds yfinance and pandas to `quant/` dependencies (done: pinned in `quant/pyproject.toml`, `uv.lock` committed).
- `close` in the snapshot is the **adjusted close** (splits and dividends), so returns computed from it are
  total returns and have no artificial jumps. Past values change whenever a new dividend or split is applied, so
  a refresh rewrites history; the Java import is `ON CONFLICT DO NOTHING` and therefore never updates stored
  closes. After a refresh, reset `daily_prices` (or add an upsert mode) before re-importing.
- `daily_prices` stays close + volume; raw OHLC can be added by a new migration if M3 needs them.
- Revisit at M3 (quant) or when yfinance stops working. DESIGN 18 "price source" can be closed.
