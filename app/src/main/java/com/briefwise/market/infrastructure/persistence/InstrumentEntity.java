package com.briefwise.market.infrastructure.persistence;

import com.briefwise.market.domain.model.InstrumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "instruments")
class InstrumentEntity {

    @Id
    private UUID id;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false)
    private InstrumentType instrumentType;

    @Column(name = "currency", nullable = false)
    private String currency;

    protected InstrumentEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public String getCurrency() {
        return currency;
    }
}
