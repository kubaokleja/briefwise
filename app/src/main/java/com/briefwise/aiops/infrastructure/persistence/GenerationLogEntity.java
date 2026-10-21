package com.briefwise.aiops.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Write-only for now: rows are inserted and never read back by the app until replay (M1 slice 5). */
@Entity
@Table(name = "ai_generation_log")
class GenerationLogEntity {

    @Id
    private UUID id;

    @Column(name = "feature", nullable = false)
    private String feature;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "prompt", nullable = false)
    private String prompt;

    @Column(name = "response")
    private String response;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "cost_usd", nullable = false)
    private BigDecimal costUsd;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;

    protected GenerationLogEntity() {
    }

    GenerationLogEntity(UUID id, String feature, String model, String promptVersion, String prompt,
                        String response, int promptTokens, int completionTokens, BigDecimal costUsd,
                        long latencyMs, String correlationId, boolean fallback) {
        this.id = id;
        this.feature = feature;
        this.model = model;
        this.promptVersion = promptVersion;
        this.prompt = prompt;
        this.response = response;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.costUsd = costUsd;
        this.latencyMs = latencyMs;
        this.correlationId = correlationId;
        this.fallback = fallback;
    }

    public UUID getId() {
        return id;
    }
}
