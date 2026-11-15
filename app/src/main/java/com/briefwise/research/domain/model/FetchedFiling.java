package com.briefwise.research.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A filing as delivered by a source: its listing, where it came from, the extractor version that produced the
 * sections and the sections in document order (main document first, then EX-99.1).
 */
public record FetchedFiling(FilingRef ref, String sourceUrl, String extractorVersion, List<FilingSection> sections) {

    public FetchedFiling {
        Objects.requireNonNull(ref, "ref");
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (extractorVersion == null || extractorVersion.isBlank()) {
            throw new IllegalArgumentException("extractorVersion must not be blank");
        }
        sections = List.copyOf(sections);
    }

    /** SHA-256 over the sections, the re-ingest key next to the accession number (ADR 0026). */
    public String contentSha256() {
        return ContentHash.of(sections);
    }
}
