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
     * Replaces the stored filing with the same accession (metadata, hash, all sections) in one transaction; the
     * stored filing id is kept.
     */
    void replace(Filing filing);
}
