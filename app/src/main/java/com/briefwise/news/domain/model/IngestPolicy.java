package com.briefwise.news.domain.model;

import java.util.Objects;

/** How far back each ingest run asks the source ({@code lookbackDays} UTC days before today) and how it dedups. */
public record IngestPolicy(int lookbackDays, DedupPolicy dedup) {

    public IngestPolicy {
        if (lookbackDays < 0) {
            throw new IllegalArgumentException("lookbackDays must not be negative, was " + lookbackDays);
        }
        Objects.requireNonNull(dedup, "dedup");
    }
}
