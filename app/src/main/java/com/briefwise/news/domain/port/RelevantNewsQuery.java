package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.RelevantNews;
import java.util.List;
import java.util.UUID;

/** Inbound port: the classified news that matters to one portfolio now (selection policy, ADR 0017). */
public interface RelevantNewsQuery {

    /** @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist */
    List<RelevantNews> relevantFor(UUID portfolioId);
}
