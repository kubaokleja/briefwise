package com.briefwise.research.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One filing as listed by the source, before it is fetched. {@code items} are the 8-K item numbers EDGAR lists
 * (e.g. {@code 2.02}); empty for 10-K / 10-Q. {@code reportDate} is the period of report, if EDGAR gives one.
 */
public record FilingRef(AccessionNo accessionNo, Cik cik, FilingType type, LocalDate filedAt,
                        Optional<LocalDate> reportDate, String primaryDocument, Set<String> items) {

    public FilingRef {
        Objects.requireNonNull(accessionNo, "accessionNo");
        Objects.requireNonNull(cik, "cik");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(filedAt, "filedAt");
        Objects.requireNonNull(reportDate, "reportDate");
        if (primaryDocument == null || primaryDocument.isBlank()) {
            throw new IllegalArgumentException("primaryDocument must not be blank");
        }
        items = Set.copyOf(items);
    }
}
