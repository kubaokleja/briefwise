package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.TokenCount;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The model's answer for one golden case. Empty {@code labels}: the answer was unusable (malformed, errored or
 * expired in the batch); such a case still counts, as a miss, so a model that fails often cannot score well by
 * answering only the easy items.
 */
public record EvalPrediction(UUID caseId, Optional<EvalLabels> labels, TokenCount tokens) {

    public EvalPrediction {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(tokens, "tokens");
    }

    public static EvalPrediction invalid(UUID caseId, TokenCount tokens) {
        return new EvalPrediction(caseId, Optional.empty(), tokens);
    }

    public boolean valid() {
        return labels.isPresent();
    }
}
