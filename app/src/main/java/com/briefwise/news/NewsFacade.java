package com.briefwise.news;

import com.briefwise.news.domain.model.IngestSummary;
import com.briefwise.news.domain.port.NewsIngest;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code news}. Planned next: classifyPending, getRelevantNews,
 * summarizeForPortfolio (M1). Methods are added with the slice that implements them; no logic here, delegate only.
 */
@Component
public class NewsFacade {

    private final NewsIngest ingest;

    NewsFacade(NewsIngest ingest) {
        this.ingest = ingest;
    }

    /**
     * Fetches news for held companies (ETFs are skipped), drops URLs already stored and stores near-duplicate
     * headlines as {@code SKIPPED} (ADR 0009). Safe to repeat.
     *
     * @throws IllegalStateException if the news source is misconfigured (e.g. no or a rejected API key)
     */
    public IngestSummaryView ingest() {
        IngestSummary s = ingest.ingest();
        return new IngestSummaryView(s.queried(), s.skipped(), s.failed(), s.fetched(), s.inserted(),
                s.nearDuplicates(), s.alreadyKnown());
    }

    /** See {@link IngestSummary} for the meaning of each count. */
    public record IngestSummaryView(List<Ticker> queried, List<Ticker> skipped, List<Ticker> failed, int fetched,
                                    int inserted, int nearDuplicates, int alreadyKnown) {}
}
