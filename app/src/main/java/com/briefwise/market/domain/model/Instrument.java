package com.briefwise.market.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@code cik} is the SEC issuer id (10 digits, zero-padded); empty for ETFs and issuers outside EDGAR. */
public record Instrument(UUID id, Ticker ticker, String name, InstrumentType type, String currency,
                         Optional<String> cik) {

    public Instrument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(cik, "cik");
    }
}
