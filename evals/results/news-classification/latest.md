## News classification eval

**Gate: PASSED** - no baseline yet; commit this run as baseline.json to start gating

Model `claude-haiku-5-5`, prompt `v1`, batch `msgbatch_01NEYjzTSHdRuxTG8jWRDVHU`, 2026-11-07T18:29:20.103206400Z

258 golden cases (150 random), 5 unusable answers (1.9%, counted as misses). Cost 0.0118 USD (batch price), 0.0457 USD per 1,000 items.

| Label | Macro F1 (all) | Accuracy (all) | Macro F1 (random) | Accuracy (random) |
|---|---|---|---|---|
| Sentiment (gated) | 0.785 | 0.860 | 0.853 | 0.907 |
| Relevance | 0.676 | 0.748 | 0.658 | 0.813 |
| Event type | 0.572 | 0.640 | 0.424 | 0.640 |

Macro F1 averages the classes equally; accuracy is dominated by the majority class (`NEUTRAL`, `LOW`, `OTHER`).

<details><summary>Sentiment: per class and confusion matrix (all rows)</summary>

| Class | Support | Predicted | Precision | Recall | F1 |
|---|---|---|---|---|---|
| NEGATIVE | 19 | 17 | 0.824 | 0.737 | 0.778 |
| NEUTRAL | 201 | 205 | 0.902 | 0.920 | 0.911 |
| POSITIVE | 38 | 31 | 0.742 | 0.605 | 0.667 |

| true \ predicted | NEGATIVE | NEUTRAL | POSITIVE | invalid |
|---|---|---|---|---|
| NEGATIVE | 14 | 5 | 0 | 0 |
| NEUTRAL | 3 | 185 | 8 | 5 |
| POSITIVE | 0 | 15 | 23 | 0 |

</details>

<details><summary>Relevance: per class and confusion matrix (all rows)</summary>

| Class | Support | Predicted | Precision | Recall | F1 |
|---|---|---|---|---|---|
| LOW | 182 | 157 | 0.924 | 0.797 | 0.855 |
| MEDIUM | 58 | 65 | 0.492 | 0.552 | 0.520 |
| HIGH | 18 | 31 | 0.516 | 0.889 | 0.653 |

| true \ predicted | LOW | MEDIUM | HIGH | invalid |
|---|---|---|---|---|
| LOW | 145 | 32 | 0 | 5 |
| MEDIUM | 11 | 32 | 15 | 0 |
| HIGH | 1 | 1 | 16 | 0 |

</details>

<details><summary>Event type: per class and confusion matrix (all rows)</summary>

| Class | Support | Predicted | Precision | Recall | F1 |
|---|---|---|---|---|---|
| EARNINGS | 21 | 20 | 0.600 | 0.571 | 0.585 |
| GUIDANCE (too few) | 4 | 5 | 0.600 | 0.750 | 0.667 |
| M_AND_A (too few) | 2 | 4 | 0.500 | 1.000 | 0.667 |
| REGULATORY (too few) | 3 | 4 | 0.500 | 0.667 | 0.571 |
| LITIGATION (too few) | 3 | 2 | 1.000 | 0.667 | 0.800 |
| MANAGEMENT (too few) | 0 | 4 | 0.000 | 0.000 | 0.000 |
| MACRO | 16 | 25 | 0.520 | 0.813 | 0.634 |
| PRODUCT | 14 | 49 | 0.224 | 0.786 | 0.349 |
| ANALYST | 14 | 19 | 0.632 | 0.857 | 0.727 |
| OTHER | 181 | 121 | 0.893 | 0.597 | 0.715 |

| true \ predicted | EARNINGS | GUIDANCE | M_AND_A | REGULATORY | LITIGATION | MANAGEMENT | MACRO | PRODUCT | ANALYST | OTHER | invalid |
|---|---|---|---|---|---|---|---|---|---|---|---|
| EARNINGS | 12 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 5 | 0 |
| GUIDANCE | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| M_AND_A | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| REGULATORY | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 1 | 0 |
| LITIGATION | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 1 | 0 |
| MACRO | 0 | 0 | 0 | 0 | 0 | 1 | 13 | 0 | 0 | 2 | 0 |
| PRODUCT | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 11 | 0 | 3 | 0 |
| ANALYST | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 1 | 0 |
| OTHER | 6 | 0 | 2 | 2 | 0 | 3 | 10 | 38 | 7 | 108 | 5 |

</details>

_Classes with fewer than 10 cases are marked: one case moves their F1 by many points._
