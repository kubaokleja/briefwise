package com.briefwise.news.infrastructure.eval;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.news.eval.*} (ADR 0018). Paths are relative to the working directory, which is the repo root
 * when the jar runs in {@code evals.yml}. {@code golden-path}: the decrypted golden set (git-ignored).
 * {@code results-dir}: where {@code latest.json} / {@code latest.md} are written and {@code baseline.json} is read.
 * {@code max-drop-points}: how far sentiment macro F1 may fall below the baseline, in percentage points (DESIGN 10).
 * {@code accept-new-baseline}: a run on a golden set other than the baseline's passes as the new reference instead of
 * failing as not comparable (ADR 0022); set it only for a deliberate golden set change, never by default.
 */
@Validated
@ConfigurationProperties("briefwise.news.eval")
record NewsEvalProperties(@DefaultValue("evals/sentiment_golden.csv") @NotNull Path goldenPath,
                          @DefaultValue("evals/results/news-classification") @NotNull Path resultsDir,
                          @DefaultValue("3.0") @PositiveOrZero double maxDropPoints,
                          @DefaultValue("false") boolean acceptNewBaseline) {
}
