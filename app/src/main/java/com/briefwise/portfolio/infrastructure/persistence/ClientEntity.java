package com.briefwise.portfolio.infrastructure.persistence;

import com.briefwise.portfolio.domain.model.RiskProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Read-only for now; audit columns (created_at/updated_at) get mapped when the first write path appears. */
@Entity
@Table(name = "clients")
class ClientEntity {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile", nullable = false)
    private RiskProfile riskProfile;

    protected ClientEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public RiskProfile getRiskProfile() {
        return riskProfile;
    }
}
