package com.briefwise.shared.domain.model;

/**
 * Pointer to the source of a factual claim ("every number has a source").
 *
 * @param sourceId identifier of the source document or row (news id, filing accession number, ...)
 * @param locator  where inside the source (section, chunk ordinal, date), for a reader to verify
 * @param excerpt  the supporting text, kept short
 */
public record Citation(SourceType type, String sourceId, String locator, String excerpt) {

    public Citation {
        if (type == null) {
            throw new IllegalArgumentException("Citation type must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("Citation sourceId must not be blank");
        }
        locator = locator == null ? "" : locator;
        excerpt = excerpt == null ? "" : excerpt;
    }
}
