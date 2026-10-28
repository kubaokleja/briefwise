package com.briefwise.news.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** What the batch classifier returns for a whole golden set (ADR 0018). */
public sealed interface BatchOutcome {

    /** The batch ended; {@code predictions} has one entry per answered case, {@code costUsd} at the batch price. */
    record Completed(String batchId, String model, String promptVersion, List<EvalPrediction> predictions,
                     BigDecimal costUsd) implements BatchOutcome {

        public Completed {
            Objects.requireNonNull(batchId, "batchId");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(promptVersion, "promptVersion");
            predictions = List.copyOf(predictions);
            Objects.requireNonNull(costUsd, "costUsd");
        }
    }

    /** Nothing was submitted (budget, few-shot leakage, no Anthropic model). */
    record Refused(String reason) implements BatchOutcome {}

    /** Submitted but not ended within {@code max-wait}; the batch id lets you fetch the results later. */
    record Incomplete(String batchId, String reason) implements BatchOutcome {}
}
