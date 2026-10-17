package com.briefwise.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "positions")
class PositionEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private PortfolioEntity portfolio;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    protected PositionEntity() {
    }

    public UUID getId() {
        return id;
    }

    public PortfolioEntity getPortfolio() {
        return portfolio;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getWeight() {
        return weight;
    }
}
