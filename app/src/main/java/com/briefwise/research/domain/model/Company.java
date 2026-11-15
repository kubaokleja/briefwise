package com.briefwise.research.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;
import java.util.Optional;

/** An instrument as research sees it: only a {@code COMPANY} with a CIK has filings (ADR 0025). */
public record Company(Ticker ticker, Kind kind, Optional<Cik> cik) {

    public enum Kind { COMPANY, FUND }

    public Company {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(cik, "cik");
    }

    public boolean filesOnEdgar() {
        return kind == Kind.COMPANY && cik.isPresent();
    }
}
