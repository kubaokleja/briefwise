package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.PortfolioSummary;
import com.briefwise.news.domain.model.SummaryOutcome;
import com.briefwise.news.domain.model.SummaryRun;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Inbound port: make and read the daily news summary per portfolio. */
public interface PortfolioSummaries {

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist */
    SummaryOutcome summarize(UUID portfolioId);

    /** Every portfolio; stops at the first deferral, or before the next portfolio once {@code deadline} has passed. */
    SummaryRun summarizeAll(Instant deadline);

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist */
    Optional<PortfolioSummary> latest(UUID portfolioId);
}
