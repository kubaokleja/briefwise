# 0009. News deduplication (D1) and news for held ETFs

- Status: Accepted
- Date: 2026-10-21

## Context

Finnhub returns the same event several times: the same URL for several queried tickers, and the same story
from different outlets with reworded headlines. Every duplicate that reaches classification costs an LLM call
and shows the RM the same thing twice. Hiding a genuinely new item is worse than showing a duplicate, so errors
should lean towards keeping items. [DESIGN 7.1](../DESIGN.md#71-news-pipeline-m1) and
[DESIGN 8](../DESIGN.md#8-ai-and-engineering-decision-points) propose URL hash + pg_trgm, with embeddings only
if the eval shows a need. No labelled duplicate sample exists yet.

Separately, 2 of the 11 held tickers are ETFs (SPY, AGG). Finnhub `company-news` covers companies only.

Trigram similarity (pg_trgm `similarity()`, 0..1) on illustrative headline pairs, computed for this decision:

| Score | Pair | Same event? |
|---|---|---|
| 0.88 | "Exxon Mobil reports Q2 earnings" / "... Q3 earnings" | No (3 months apart) |
| 0.81 | "Apple beats Q3 estimates as iPhone sales rise" / "..., shares up 3%" | Yes |
| 0.58 | "JPMorgan raises dividend" / "... to $1.40 per share" | Yes |
| 0.43 | "Nvidia shares fall 5% after export restrictions" / "... 3% after earnings miss" | No |
| 0.39 | "Microsoft to acquire Activision for $69 billion" / "Microsoft agrees to buy Activision Blizzard in $69B deal" | Yes |
| 0.19 | "Apple beats Q3 estimates..." / "Apple tops third-quarter forecasts on strong iPhone demand" | Yes |

## Options

**Threshold and window** (compared within the same ticker only):
1. 0.6 within 48 h: catches suffix and rewording variants (0.81), keeps different stories apart (0.43); the
   window keeps recurring headlines (quarterly earnings, 0.88) apart. Misses true paraphrases (0.39, 0.19).
2. 0.7 within 48 h: fewer wrong merges, more duplicates classified (fractions of a cent each).
3. 0.5 within 72 h: catches more, but close to merging different stories (0.43).
4. URL hash only until labelled data exists: simplest, every outlet's copy is classified.

**What to do with a near-duplicate:**
1. Store it with status `SKIPPED` and `duplicate_of_id` -> original: auditable, measurable, never classified.
2. Drop it: smaller table, but what dedup removed cannot be seen or measured.

**News for held ETFs:**
1. None for now: query companies only, skip ETFs explicitly.
2. Finnhub general/market news once per run, attached to held ETFs as macro context: more items to classify,
   vague relevance.
3. News of the ETF's top holdings: needs holdings data we do not have; overlaps held stocks.

## Decision

- **D1: 0.6 within 48 h, same ticker** (`briefwise.news.dedup.*`), provisional until checked against the hand-
  labelled sample (rows marked `duplicate_of_id`, [evals/labelling](../../evals/labelling/README.md)).
- Exact duplicates by **SHA-256 of the normalised URL** (scheme and host lower-cased, fragment dropped; path and
  query kept). A known URL fetched for another ticker only adds that ticker (`news_item_tickers`).
- A near-duplicate is **stored as `SKIPPED` with `duplicate_of_id`**. It is compared against originals only, and
  articles are processed oldest first, so the earliest version is the original.
- **ETFs get no news for now** (option 1). Strategy to include them later, to be decided with the morning brief
  (M4), where macro context is consumed: start with option 2 (general news, one call per run, classified as
  `MACRO`), and consider option 3 only if a holdings source appears. Logged in the Domain Questions Log.
- **No trigram index on the headline.** PLANNING listed "pg_trgm index on headline", but the dedup query first
  narrows candidates to one ticker and a 96 h window (indexes on `news_item_tickers.ticker` and
  `news_items.published_at`), so a GIN index would not be used and only slows inserts. Add it when a query
  searches headlines across all items (e.g. headline search in the UI).

## Consequences

- No LLM or embedding cost for dedup; deterministic and testable (`NewsRepositoryAdapterIT` pins the example pairs).
- Paraphrased duplicates (0.39, 0.19) still reach classification. Revisit with embeddings only if the labelled
  sample shows a meaningful rate.
- Threshold and window are configuration; changing them needs no migration, but already stored `SKIPPED` items
  stay as they are.
- Held ETFs have no news in the feed or the summary until the M4 decision.
