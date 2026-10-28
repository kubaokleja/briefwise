package com.briefwise.aiops.domain.port;

import com.briefwise.aiops.domain.model.NewGeneration;
import com.briefwise.shared.domain.model.PricingTier;
import java.util.UUID;

/** Inbound port: the audit trail of LLM calls ({@code ai_generation_log}). */
public interface GenerationLog {

    /**
     * Stores the call with its cost (at {@code tier}'s price) and the current correlation ID.
     *
     * @return the ID of the log entry
     * @throws com.briefwise.aiops.domain.model.UnpricedModelException if the model has no configured price
     */
    UUID record(NewGeneration generation, PricingTier tier);
}
