# 0010. Golden set labelling for news classification

- Status: Accepted
- Date: 2026-10-23

## Context

The news classifier (M1 slice 4) is evaluated in slice 8 against a labelled golden set
([DESIGN 8](../DESIGN.md#8-ai-and-engineering-decision-points), [DESIGN 10](../DESIGN.md#10-evaluation-strategy)
plans `evals/sentiment_golden.csv`, 150 hand-labelled, macro F1). Few-shot examples must never come from the golden
set. Three things had to be settled before labelling:

- **Definitions.** `Relevance` was "relative to what: decided in slice 4"; nothing said whether sentiment means tone
  or price effect, or how to label an item tagged with several tickers. The prompt and the labels must use the same
  definitions, or the eval measures a mismatch of definitions.
- **Sampling.** A 30-day ingest (2026-10-23: 1,908 new items, 9 held companies) exported at random gave 150 golden
  rows that were 84% `NEUTRAL`, 77% `LOW`, 5 `HIGH`, and had no `M_AND_A` or `MANAGEMENT`. Finnhub tags tickers
  loosely, so most items are passing mentions. Macro F1 averages per-class scores: with 5 `HIGH` rows one error
  moves that class by about 20 points.
- **Who labels.** 344 rows by hand was too much effort for the maintainer. The classifier is itself an LLM (Claude
  Haiku is the L2 starting proposal), so LLM-made labels can share its biases.

## Options

**Sampling**
1. *Keep the random sample.* Honest picture of the feed; rare classes too small for per-class metrics.
2. *Stratified company sample added to the random one.* Headlines naming the held company are a proxy for
   company-specific news and fill rare classes; the mix is no longer the real distribution, so results need the
   `random` subset reported separately. Same labelling effort per row.
3. *Longer ingest (free tier: one year).* More rare events, but many more rows to label and older news.

**Labelling**
1. *Maintainer labels everything by hand.* Independent ground truth; about 1–2 hours for 344 rows.
2. *Claude drafts, maintainer reviews a sample.* Fast and consistent with the written definitions; risk of shared
   bias with an LLM classifier, mitigated by the review and by recording provenance.
3. *Two independent labellers + agreement.* Best quality; no second labeller exists.

## Decision

**Definitions** (full table in [`evals/labelling/README.md`](../../evals/labelling/README.md)):
- `sentiment`: likely effect on the ticker's price from a shareholder's view, not the article's tone. Opinion or
  prediction pieces with no new facts are `NEUTRAL`.
- `relevance`: relative to the single ticker: "would an RM whose client holds it want to see it this morning?"
  `HIGH` = company-specific and likely price-moving; `MEDIUM` = company-specific but minor; `LOW` = passing mention,
  generic market talk, clickbait.
- `event_type`: the main event; analyst rating changes and customer contracts go to `OTHER` with a note.
- Multi-ticker items: one label from the view of the most affected ticker, named in `notes`.
- Label from headline + summary only (what the model sees).

**Sampling:** option 2. 200 random rows (`sample=random`) plus 16 headlines per held company that name it
(`sample=company`, `export_company_headlines.sql` + `append_company_sample.py`); 344 rows, 258 golden / 86 few-shot,
split 3:1, rows never move between splits. Golden distribution: 18 `HIGH`, 58 `MEDIUM`, 182 `LOW`; 19 `NEGATIVE`,
38 `POSITIVE`.

**Labelling:** option 2. Claude (Opus 5.5) drafted all labels before any classification prompt existed; the
maintainer spot-checked about 40 rows on 2026-10-23 with no disagreements. Provenance is in
the labelling README.

## Consequences

- Sentiment and relevance have enough rows per class for per-class metrics; slice 8 reports them per class and on
  `random` rows alone. This replaces "150 hand-labelled" in DESIGN 10.
- Event types stay thin (`GUIDANCE` 4, `REGULATORY` 3, `LITIGATION` 3, `M_AND_A` 2, `MANAGEMENT` 0 golden rows):
  slice 8 decides (E1) between accuracy with per-class counts and a longer ingest for rare classes.
- Agreement with these labels may overstate a Claude classifier's quality. Before the L2 model ADR relies on the
  eval, a recorded human review (sample size + agreement rate) is required; a non-Claude baseline (Ollama, Alpha
  Vantage) helps show whether scores are inflated.
- The definitions bind the slice 4 prompt. Open follow-ups (Domain Questions Log): `ANALYST` / `CONTRACT` event
  types, per-ticker vs per-item classifier output.
- Labelling also showed that same-event duplicates score 0.09–0.49 on headline trigram similarity (ADR 0009 uses
  0.6): trigram dedup only catches re-syndication. Revisit D1 with these 7 pairs after the eval.
- The labelled CSV stays git-ignored (Finnhub licence) until publishing is decided.
- Revisit when the classifier output shape changes, when event types are added, or if the human review finds
  systematic disagreement.
