package com.briefwise.aiops.domain.port;

import com.briefwise.aiops.domain.model.NewGeneration;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: stored generation log entries. */
public interface GenerationRepository {

    /** @return the ID of the new entry */
    UUID save(NewGeneration generation, BigDecimal costUsd, Optional<String> correlationId);
}
