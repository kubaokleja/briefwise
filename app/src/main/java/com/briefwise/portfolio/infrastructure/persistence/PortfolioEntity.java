package com.briefwise.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolios")
class PortfolioEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientEntity client;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency;

    @OneToMany(mappedBy = "portfolio")
    @OrderBy("weight DESC, ticker ASC")
    private List<PositionEntity> positions = new ArrayList<>();

    protected PortfolioEntity() {
    }

    public UUID getId() {
        return id;
    }

    public ClientEntity getClient() {
        return client;
    }

    public String getName() {
        return name;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public List<PositionEntity> getPositions() {
        return positions;
    }
}
