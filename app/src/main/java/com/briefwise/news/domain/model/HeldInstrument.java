package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;

/** A ticker held in at least one portfolio. Only companies get company news; funds (ETFs) are skipped for now. */
public record HeldInstrument(Ticker ticker, Kind kind) {

    public enum Kind { COMPANY, FUND }

    public HeldInstrument {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(kind, "kind");
    }
}
