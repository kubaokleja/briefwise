package com.briefwise.market.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_prices")
class DailyPriceEntity {

    @Id
    private UUID id;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close", nullable = false)
    private BigDecimal close;

    @Column(name = "volume", nullable = false)
    private long volume;

    protected DailyPriceEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public BigDecimal getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }
}
