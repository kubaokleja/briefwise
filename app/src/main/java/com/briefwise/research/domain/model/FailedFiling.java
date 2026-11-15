package com.briefwise.research.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;
import java.util.Optional;

/**
 * A filing (or, without an accession, a company's listing) that could not be ingested. The run went on with the
 * next one; {@code reason} is the exception type and message.
 */
public record FailedFiling(Ticker ticker, Optional<AccessionNo> accessionNo, String reason) {

    public FailedFiling {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(accessionNo, "accessionNo");
        Objects.requireNonNull(reason, "reason");
    }
}
