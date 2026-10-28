package com.briefwise.news.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Code gate before a summary call (DESIGN 7.4 layer 1, ADR 0017): the LLM is never called without a reason. A new
 * summary is made when there is none for today (UTC); when today's summary was a template fallback or empty and the
 * selection now has items; or when a HIGH item in the selection was classified after today's summary was made. A
 * new MEDIUM item alone does not regenerate a non-empty summary.
 */
public final class RegenerationGate {

    private RegenerationGate() {
    }

    public static boolean needsNewSummary(Optional<PortfolioSummary> latest, List<RelevantNews> selection,
                                          LocalDate today) {
        if (latest.isEmpty() || !latest.get().summaryDate().equals(today)) {
            return true;
        }
        PortfolioSummary current = latest.get();
        if (selection.isEmpty()) {
            return false;
        }
        if (current.fallback() || current.empty()) {
            return true;
        }
        return selection.stream().anyMatch(r -> r.news().relevance() == Relevance.HIGH
                && r.news().classifiedAt().isAfter(current.generatedAt()));
    }
}
