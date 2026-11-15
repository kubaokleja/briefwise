package com.briefwise.research.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;

/** One filing after ingest: what was done and how many of its sections the injection screen flagged. */
public record IngestedFiling(Ticker ticker, FilingRef ref, IngestOutcome outcome, int sections,
                             int suspectedSections) {

    public IngestedFiling {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(outcome, "outcome");
        if (sections < 0 || suspectedSections < 0 || suspectedSections > sections) {
            throw new IllegalArgumentException("Invalid section counts " + suspectedSections + " of " + sections);
        }
    }
}
