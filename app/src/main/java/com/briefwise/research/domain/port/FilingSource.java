package com.briefwise.research.domain.port;

import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.FilingRef;
import java.util.List;

/** Outbound port: where filings come from, live EDGAR or the committed snapshot (ADR 0026). */
public interface FilingSource {

    /**
     * The company's filings of the ingested forms (amendments and other forms left out), in any order.
     *
     * @throws com.briefwise.research.domain.model.FilingSourceUnavailableException if the source fails after retries
     * @throws IllegalStateException if the source is misconfigured (e.g. no {@code User-Agent})
     */
    List<FilingRef> list(Cik cik);

    /**
     * The extracted sections of one listed filing.
     *
     * @throws com.briefwise.research.domain.model.FilingNotFoundException if the source has no such filing
     * @throws com.briefwise.research.domain.model.FilingSourceUnavailableException if the source fails after retries
     */
    FetchedFiling fetch(FilingRef ref);
}
