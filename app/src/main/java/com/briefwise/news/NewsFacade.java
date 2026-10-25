package com.briefwise.news;

import com.briefwise.news.domain.model.IngestSummary;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.RelaySummary;
import com.briefwise.news.domain.port.NewsIngest;
import com.briefwise.news.domain.port.NewsProcessing;
import com.briefwise.news.domain.port.OutboxRelay;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code news}. Planned next: classifyPending, getRelevantNews,
 * summarizeForPortfolio (M1). Methods are added with the slice that implements them; no logic here, delegate only.
 */
@Component
public class NewsFacade {

    private final NewsIngest ingest;
    private final OutboxRelay relay;
    private final NewsProcessing processing;

    NewsFacade(NewsIngest ingest, OutboxRelay relay, NewsProcessing processing) {
        this.ingest = ingest;
        this.relay = relay;
        this.processing = processing;
    }

    /**
     * Fetches news for held companies (ETFs are skipped), drops URLs already stored and stores near-duplicate
     * headlines as {@code SKIPPED} (ADR 0009). Each new {@code RECEIVED} item gets an outbox row in the same
     * transaction (ADR 0011). Safe to repeat.
     *
     * @throws IllegalStateException if the news source is misconfigured (e.g. no or a rejected API key)
     */
    public IngestSummaryView ingest() {
        IngestSummary s = ingest.ingest();
        return new IngestSummaryView(s.queried(), s.skipped(), s.failed(), s.fetched(), s.inserted(),
                s.nearDuplicates(), s.alreadyKnown());
    }

    /**
     * Sends pending outbox rows to Kafka {@code news.raw}, at least once (ADR 0011). If the broker is unavailable the
     * rows stay pending for the next run. Called by the relay scheduler and, from M1 slice 6, the job runner.
     */
    public PublishSummaryView publishPending() {
        RelaySummary s = relay.relayPending();
        return new PublishSummaryView(s.published(), s.pending(), s.brokerUnavailable());
    }

    /**
     * Handles one {@code news.raw} message; called by the Kafka listener. Code gate (still {@code RECEIVED}, a ticker
     * still held), injection screening, then LLM classification (ADR 0012/0013): the item ends {@code CLASSIFIED},
     * {@code UNCLASSIFIED} (fallback) or {@code SKIPPED}, or stays {@code RECEIVED} if the budget is used up.
     * Idempotent: an item that is no longer {@code RECEIVED} is left alone (K3, ADR 0011).
     *
     * @throws NewsItemNotFoundException if no item has this id (the message goes to the DLT)
     */
    public void processIngested(UUID newsId) {
        processing.process(newsId);
    }

    /** See {@link IngestSummary} for the meaning of each count. */
    public record IngestSummaryView(List<Ticker> queried, List<Ticker> skipped, List<Ticker> failed, int fetched,
                                    int inserted, int nearDuplicates, int alreadyKnown) {}

    /** See {@link RelaySummary}. */
    public record PublishSummaryView(int published, int pending, boolean brokerUnavailable) {}
}
