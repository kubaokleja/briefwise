package com.briefwise.news.domain.model;

import java.util.Objects;

/** What the classifier port returns for one item. */
public sealed interface ClassifierResult {

    /** The model answered with a valid classification. */
    record Classified(NewsClassification classification) implements ClassifierResult {

        public Classified {
            Objects.requireNonNull(classification, "classification");
        }
    }

    /** No valid answer after the retry: the item falls back to {@code UNCLASSIFIED}. */
    record Failed(UnclassifiedReason reason) implements ClassifierResult {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Not called now, e.g. today's budget is used up (ADR 0008) or no chat model is configured: the item stays
     * {@code RECEIVED} for a later run.
     */
    record Deferred(String reason) implements ClassifierResult {

        public Deferred {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
