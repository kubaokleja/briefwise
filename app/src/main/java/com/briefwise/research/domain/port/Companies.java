package com.briefwise.research.domain.port;

import com.briefwise.research.domain.model.Company;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;

/** Outbound port: instruments with their CIK (market) and which of them the portfolios hold. */
public interface Companies {

    /**
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the ticker is unknown
     */
    Company get(Ticker ticker);

    /** Instruments held in at least one portfolio, sorted by ticker, funds included. */
    List<Company> held();
}
