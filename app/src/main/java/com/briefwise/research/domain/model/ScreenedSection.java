package com.briefwise.research.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A section with its id (also the screening content id) and the screening result. */
public record ScreenedSection(UUID id, FilingSection section, Screening screening) {

    public ScreenedSection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(screening, "screening");
    }
}
