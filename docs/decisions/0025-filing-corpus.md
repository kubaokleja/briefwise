# 0025. Filing corpus: held stocks, about one year, selected sections

- Status: Accepted
- Date: 2026-11-14

## Context

M2 answers "why is X down?" from SEC filings, classified news and the market move
([DESIGN 7.3](../DESIGN.md#73-qa-with-citations-m2)). The corpus decides what the retrieval can find at all, how
long ingest takes and how much of Neon's 0.5 GB free tier it uses after embedding (slice 3). Open items:
[DESIGN 18](../DESIGN.md#18-open-questions) "European/Swiss companies (F3)", and ETFs, which have no company news
([ADR 0009](0009-news-dedup-and-etf-news.md)) and, except trust-type funds, no 10-K.

Rough size (one year, selected sections): about 0.5 MB of text per company, and 5–6x that once chunks carry vectors
and indexes.

## Options

**Companies**
1. **All 30 stocks, about one year.** Any ticker can be asked about. About 16 MB of text and about 80–100 MB in Neon
   once embedded.
2. **Held stocks only, about one year.** These are the 9 companies held by the synthetic portfolios. About a third of
   the size, faster ingest and a smaller golden set (slice 4). Questions on a non-held ticker get "I don't know".
3. **All 30 stocks, about two years.** This allows year-over-year questions, but needs about 200 MB in Neon, which
   crowds the free tier once news and logs grow.

**8-K content**
1. **Items plus the EX-99.1 exhibit.** The earnings press release is the exhibit; Item 2.02 itself usually says only
   "see Exhibit 99.1". This costs one more request per 8-K.
2. **Item text only.** Simpler, but earnings 8-Ks then carry almost no content.
3. **No 8-Ks.** Only 10-K and 10-Q, which loses the most timely filing source for a move.

**Tables**
1. **Kept as `cell | cell` text rows.** MD&A numbers stay retrievable and can later back number grounding
   (slice 7). Rows can be noisy for embeddings.
2. **Dropped.** Cleaner prose, but numbers are lost.

## Decision

- **Companies:** the held stocks only (option 2).
  - The set is computed at run time from the portfolios, as for news, so a new holding is picked up without a
    change here.
  - Today that set is AAPL, AMZN, JNJ, JPM, KO, MSFT, NVDA, PG and XOM.
  - ETFs are skipped (ADR 0009). European and Swiss issuers are out of scope: they do not file 10-K, 10-Q or 8-K
    on EDGAR. This closes the M2 part of DESIGN 18 "European/Swiss companies".
- **Window:** the latest 10-K, the 10-Qs filed after it, and the 8-Ks filed in the last 12 months. Amendments (`/A`)
  are skipped.
- **Sections:**
  - 10-K: Items 1, 1A, 7 and 7A.
  - 10-Q: Part I Item 2 and Part II Item 1A.
  - 8-K: only filings with Item 2.02, 7.01 or 8.01. Their item text plus the EX-99.1 exhibit are stored.
- **Extraction:**
  - The table of contents is skipped: where an item heading appears more than once, the occurrence with the longest
    following body wins.
  - Tables are kept as `cell | cell` rows.
  - Inline-XBRL hidden headers (`ix:header`, hidden elements) are dropped.
  - An `extractor_version` is stored per filing.

## Consequences

- About 5 MB of text; Neon size stays small after embedding.
- The golden set (slice 4) covers 9 companies. Questions about the other 21 stocks are expected to return
  "I don't know", which is a useful unanswerable case.
- A portfolio change adds a company on the next ingest. A company that is no longer held keeps its filings; no
  cleanup yet (M6 retention).
- Revisit when the RAG eval (slice 9) shows misses caused by missing sections or by table noise, or when
  year-over-year questions are wanted.

## Update 2026-11-15: extraction on the held issuers

A probe of the latest 10-K, 10-Q and earnings 8-K of all 9 held stocks against live EDGAR showed:

- **Page furniture** broke the "longest body wins" rule. MSFT and JPM print running headers (`PART I` / `Item 1`)
  on every page, so every page became a section (MSFT Item 1A: 5.9K instead of 81K characters). Extractor
  version 2 also drops running headers (any heading shape repeated 3 or more times verbatim), bare page numbers,
  `Table of Contents` links and footers (the same text before a rising page number). A heading title cut after a
  connecting word takes the next line.
- **Cross-referenced MD&A, accepted as a gap.** JPM and XOM put their MD&A (and JPM its 10-Q MD&A) in a
  financial section outside any `Item` heading. Their Item 7 is a pointer of a few hundred characters ("appears on
  pages 46–160"), stored as is. Risk factors, 10-Qs (XOM) and 8-K releases are still in the corpus. Options
  considered: a per-issuer title map (deterministic, maintained by hand) and a generic title fallback (the end of
  the section is guessed). Revisit if the RAG golden set (slice 4) or eval (slice 9) shows misses on JPM / XOM
  results questions.
- **XOM has no 10-K under its new CIK** 2115436 (holding company formed in 2026; the 2025 10-K is under 34088).
  Only the new CIK is ingested: 10-Qs and 8-Ks now, the 10-K from about February 2027. No predecessor-CIK column.
- JNJ's latest 10-Q has no Part II Item 1A (allowed when nothing changed); PG's and KO's are short pointers.
