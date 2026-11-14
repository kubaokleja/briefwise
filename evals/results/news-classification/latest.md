## News classification eval

**Gate: PASSED** (PASSED) - sentiment macro F1 0.809 vs baseline 0.785 (+2.4 pp, limit -3.0 pp); prompt v2 vs baseline v1

Model `claude-haiku-5-5`, prompt `v2`, batch `msgbatch_01Hh7HbySAHjNB6vJKSH4eGe`, 2026-11-14T20:10:41.692263200Z

258 golden cases (150 random, golden set `a80c98dd1486`), 1 unusable answers (0.4%, counted as misses). Cost 0.0119 USD (batch price), 0.0461 USD per 1,000 items.

| Label | Macro F1 (all) | Accuracy (all) | Macro F1 (random) | Accuracy (random) |
|---|---|---|---|---|
| Sentiment (gated) | 0.809 | 0.888 | 0.804 | 0.907 |
| Relevance | 0.657 | 0.756 | 0.646 | 0.847 |
| Event type | 0.584 | 0.694 | 0.473 | 0.687 |

Macro F1 averages the classes equally; accuracy is dominated by the majority class (`NEUTRAL`, `LOW`, `OTHER`).

<details><summary>Sentiment: per class and confusion matrix (all rows)</summary>

| Class | Support | Predicted | Precision | Recall | F1 |
|---|---|---|---|---|---|
| NEGATIVE | 19 | 18 | 0.833 | 0.789 | 0.811 |
| NEUTRAL | 201 | 207 | 0.918 | 0.945 | 0.931 |
| POSITIVE | 38 | 32 | 0.750 | 0.632 | 0.686 |

| true \ predicted | NEGATIVE | NEUTRAL | POSITIVE | invalid |
|---|---|---|---|---|
| NEGATIVE | 15 | 4 | 0 | 0 |
| NEUTRAL | 2 | 190 | 8 | 1 |
| POSITIVE | 1 | 13 | 24 | 0 |

</details>

<details><summary>Relevance: per class and confusion matrix (all rows)</summary>

| Class | Support | Predicted | Precision | Recall | F1 |
|---|---|---|---|---|---|
| LOW | 182 | 153 | 0.961 | 0.808 | 0.878 |
| MEDIUM | 58 | 70 | 0.471 | 0.569 | 0.516 |
| HIGH | 18 | 34 | 0.441 | 0.833 | 0.577 |

| true \ predicted | LOW | MEDIUM | HIGH | invalid |
|---|---|---|---|---|
| LOW | 147 | 34 | 0 | 1 |
| MEDIUM | 6 | 33 | 19 | 0 |
| HIGH | 0 | 3 | 15 | 0 |

</details>

<details><summary>Event type: per class and confusion matrix (all rows)</summary>

| Class | Support | Predicted | Precision | Recall | F1 |
|---|---|---|---|---|---|
| EARNINGS | 21 | 21 | 0.619 | 0.619 | 0.619 |
| GUIDANCE (too few) | 4 | 6 | 0.500 | 0.750 | 0.600 |
| M_AND_A (too few) | 2 | 4 | 0.500 | 1.000 | 0.667 |
| REGULATORY (too few) | 3 | 4 | 0.750 | 1.000 | 0.857 |
| LITIGATION (too few) | 3 | 1 | 1.000 | 0.333 | 0.500 |
| MANAGEMENT (too few) | 0 | 3 | 0.000 | 0.000 | 0.000 |
| MACRO | 16 | 24 | 0.542 | 0.813 | 0.650 |
| PRODUCT | 14 | 47 | 0.255 | 0.857 | 0.393 |
| ANALYST | 14 | 19 | 0.684 | 0.929 | 0.788 |
| OTHER | 181 | 128 | 0.930 | 0.657 | 0.770 |

| true \ predicted | EARNINGS | GUIDANCE | M_AND_A | REGULATORY | LITIGATION | MANAGEMENT | MACRO | PRODUCT | ANALYST | OTHER | invalid |
|---|---|---|---|---|---|---|---|---|---|---|---|
| EARNINGS | 13 | 3 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 4 | 0 |
| GUIDANCE | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| M_AND_A | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| REGULATORY | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| LITIGATION | 0 | 0 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 |
| MACRO | 0 | 0 | 0 | 0 | 0 | 1 | 13 | 0 | 0 | 2 | 0 |
| PRODUCT | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 0 | 2 | 0 |
| ANALYST | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0 | 0 |
| OTHER | 6 | 0 | 2 | 0 | 0 | 2 | 10 | 35 | 6 | 119 | 1 |

</details>

_Classes with fewer than 10 cases are marked: one case moves their F1 by many points._
