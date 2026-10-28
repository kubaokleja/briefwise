package com.briefwise.news.domain.port;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Outbound port: the portfolios and their weights (implemented over the portfolio facade). */
public interface PortfolioHoldings {

    /** Every portfolio id, in a stable order. */
    List<UUID> portfolioIds();

    /**
     * Weight per held ticker.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist
     */
    Map<Ticker, BigDecimal> weights(UUID portfolioId);
}
