package com.briefwise.market.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;
import java.util.UUID;

public record Instrument(UUID id, Ticker ticker, String name, InstrumentType type, String currency) {

    public Instrument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
    }
}
