package com.briefwise.research.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A stored filing: provenance (accession, source URL), the content hash and the screened sections. */
public record Filing(UUID id, Ticker ticker, FilingRef ref, String sourceUrl, String extractorVersion,
                     String contentSha256, List<ScreenedSection> sections) {

    public Filing {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(extractorVersion, "extractorVersion");
        Objects.requireNonNull(contentSha256, "contentSha256");
        sections = List.copyOf(sections);
    }

    public long suspectedSections() {
        return sections.stream().filter(s -> s.screening().suspected()).count();
    }
}
