package com.briefwise.research.domain.port;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Filing;
import java.util.Optional;

/** Outbound port: stored filings and their sections. */
public interface FilingRepository {

    /** Content hash of the stored filing, empty if the accession is not stored yet. */
    Optional<String> findContentSha256(AccessionNo accessionNo);

    /** The stored filing with its sections in filing order, empty if the accession is not stored. */
    Optional<Filing> findByAccession(AccessionNo accessionNo);

    /** Stores a new filing with its sections. */
    void insert(Filing filing);

    /**
     * Updates only where the stored filing came from and which extractor produced it, for a re-ingest whose content
     * hash is unchanged; sections and screening stay as they are.
     *
     * @throws IllegalStateException if no filing with this accession is stored
     */
    void updateProvenance(AccessionNo accessionNo, String sourceUrl, String extractorVersion);

    /**
     * Replaces the stored filing with the same accession (metadata, hash, all sections) in one transaction; the
     * stored filing id is kept.
     */
    void replace(Filing filing);
}
