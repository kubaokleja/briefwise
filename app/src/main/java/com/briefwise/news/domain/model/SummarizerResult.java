package com.briefwise.news.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** What the summarizer port returns for one portfolio. */
public sealed interface SummarizerResult {

    /**
     * The model answered with a valid draft (not yet compliance-checked). {@code generationId}: its generation log
     * entry, so the caller can flag it if the draft ends in the template fallback; empty for a replayed answer (not
     * logged) or when logging failed.
     */
    record Drafted(SummaryDraft draft, Optional<UUID> generationId) implements SummarizerResult {

        public Drafted {
            Objects.requireNonNull(draft, "draft");
            Objects.requireNonNull(generationId, "generationId");
        }

        public Drafted(SummaryDraft draft) {
            this(draft, Optional.empty());
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
