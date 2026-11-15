# 0026. Filing source: live EDGAR plus a committed text snapshot; CIK on instruments

- Status: Accepted
- Date: 2026-11-14

## Context

Filings come from SEC EDGAR ([DESIGN 9](../DESIGN.md#9-data)). The SEC fair-access rules, checked on 2026-11-14 at
sec.gov "Accessing EDGAR Data":

- at most 10 requests per second;
- a `User-Agent` naming the requester and a contact email;
- no API key.

Re-checked on 2026-11-15 while building the adapter: unchanged. The 8-K exhibit EX-99.1 is found through the Type
column of the filing's `{accession}-index.htm` page (the folder `index.json` lists file names only).

EDGAR identifies companies by CIK, not by ticker. CI, the `demo` profile and the RAG golden-set anchors (slice 4)
must work offline and reproducibly ([ADR 0003](0003-runtime-github-actions-and-neon.md)), the same reason prices
use a snapshot ([ADR 0004](0004-market-price-source-yfinance-snapshot.md)). Filing ingest runs on demand, locally,
and is not part of the morning pipeline ([DESIGN 7.3](../DESIGN.md#73-qa-with-citations-m2)). EDGAR data is free to
access. The SEC pages state no explicit licence; company filings are public disclosures and are widely redistributed.

## Options

**Source**
1. **Live EDGAR only.** Simplest. But the database is the only copy, and CI and the demo have no corpus without the
   network.
2. **Live EDGAR plus a committed snapshot of the extracted text.** Reproducible offline, and the snapshot is a
   reviewable diff when the extractor changes. It costs a few MB in git and a second source adapter.
3. **Snapshot only, written by a script.** No EDGAR adapter in the app, so less to build and less to learn.

**CIK mapping**
1. **A `cik` column on market `instruments`**, seeded by Flyway and read through `MarketFacade`. This keeps one
   universe source.
2. **A research-owned `research_issuers` table.** Market stays untouched, but a second copy of the universe has to
   stay in sync.
3. **A runtime lookup of `company_tickers.json`.** Nothing to seed, but it adds a network dependency, and the SEC does
   not guarantee its accuracy.

**Rate limit**
1. **A minimum gap between requests inside the adapter** (one thread, sequential ingest). No dependency.
2. **Resilience4j `RateLimiter`.** This means a new dependency, which [ADR 0005](0005-resilience-spring-retryable.md)
   rejected.

## Decision

- **Source: option 2.**
  - `EdgarFilingSource`, a `RestClient` adapter, reads `data.sec.gov` (submissions) and `www.sec.gov` (archives).
  - The `User-Agent` comes from `SEC_USER_AGENT`; a blank value fails before any call is made.
  - On 429, 5xx and timeouts it retries with backoff (ADR 0005).
  - The EDGAR path writes `data/filings/{TICKER}/{accession}.json` (metadata plus extracted sections, no raw HTML).
  - `SnapshotFilingSource` reads that snapshot. `briefwise.research.filings.source` is `snapshot` in `test`, `demo`
    and `cloud`, and `edgar` only for a local refresh.
- **CIK: option 1.**
  - Migration V17 adds `instruments.cik` (10 digits, nullable, unique, NULL for ETFs).
  - It seeds the 30 stocks, hand-checked against `company_tickers.json`.
  - `MarketFacade` exposes the CIK as an `Optional`.
- **Rate limit: option 1.**
  - The gap between requests is configurable, defaulting to 150 ms (about 6–7 req/s, below the limit).
  - This is the "simple backoff" ADR 0005 allows, so that ADR stays valid. It would be superseded only if
    concurrent fetching needs a shared limiter.
- **Re-ingest:**
  - Filings are idempotent by accession number.
  - The same SHA-256 over the extracted sections means the filing is skipped.
  - A different hash (the extractor changed) replaces the sections in one transaction; slice 3 re-chunks them.
- **Where ingest runs:** the local database now. Neon is loaded in slice 3, once chunks exist.

## Consequences

- CI and the demo never touch EDGAR. A snapshot refresh is a deliberate local run that shows up as a git diff.
- One more column in market, and research depends on `MarketFacade` for the CIK.
- Live-only fixtures (trimmed real filings) are needed for the WireMock tests, because the extractor must be tested
  on real HTML.
- Revisit:
  - if the snapshot grows past about 50 MB (a wider corpus, ADR 0025);
  - if the SEC changes its access rules;
  - if the licence position of redistributing filing text changes.

## Update 2026-11-15: first live ingest

- The first local ingest of the held stocks: 110 filings (8 10-K, 18 10-Q, 84 8-K), 204 sections, 3.97 M characters,
  0 sections flagged by the screen; snapshot 3.9 MB in 110 files, tables 2.3 MB. Live EDGAR about 2 minutes, from the
  snapshot 9 s.
- Re-ingest refined: the same SHA-256 keeps the sections and their screening, but a new `extractor_version` or
  source URL is still recorded (`FilingRepository.updateProvenance`). Otherwise the database and the snapshot disagree
  on which extractor produced a filing after an extractor change that leaves its text alone.
- `HtmlText` v2: every Unicode space separator becomes a plain space. Filing-agent 8-Ks write `Item&#8201;8.01`
  (thin space), and 10 of them had no section under v1.
