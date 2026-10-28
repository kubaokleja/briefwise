package com.briefwise.news.domain.model;

import java.util.Objects;

/** What the summarizer port returns for one portfolio. */
public sealed interface SummarizerResult {

    /** The model answered with a valid draft (not yet compliance-checked). */
    record Drafted(SummaryDraft draft) implements SummarizerResult {

        public Drafted {
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** No valid answer after the re-ask, or a provider error: the caller falls back to the template. */
    record Failed(String reason) implements SummarizerResult {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Not called now (budget used up, no chat model, replay miss): the previous summary stays. */
    record Deferred(String reason) implements SummarizerResult {

        public Deferred {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
