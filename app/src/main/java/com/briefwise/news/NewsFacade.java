package com.briefwise.news;

import com.briefwise.news.domain.model.IngestSummary;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.RelaySummary;
import com.briefwise.news.domain.model.SweepSummary;
import com.briefwise.news.domain.port.NewsConsumerLag;
import com.briefwise.news.domain.port.NewsIngest;
import com.briefwise.news.domain.port.NewsProcessing;
import com.briefwise.news.domain.port.OutboxRelay;
import com.briefwise.news.domain.port.PendingClassification;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code news}. Planned next: getRelevantNews, summarizeForPortfolio
 * (M1 slice 7). Methods are added with the slice that implements them; no logic here, delegate only.
 */
@Component
public class NewsFacade {

    private final NewsIngest ingest;
    private final OutboxRelay relay;
    private final NewsProcessing processing;
    private final PendingClassification pending;
    private final NewsConsumerLag consumerLag;

    NewsFacade(NewsIngest ingest, OutboxRelay relay, NewsProcessing processing, PendingClassification pending,
               NewsConsumerLag consumerLag) {
        this.ingest = ingest;
        this.relay = relay;
        this.processing = processing;
        this.pending = pending;
        this.consumerLag = consumerLag;
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
     * rows stay pending for the next run. Called by the relay scheduler and the jobs.
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

    /**
     * Sweep of items stuck in {@code RECEIVED} (ADR 0016): reopens recent {@code PROVIDER_ERROR} items, then
     * classifies old {@code RECEIVED} items (DLT'd, never delivered, deferred on budget) through the same code gate
     * as {@link #processIngested}. An item that fails {@code max-attempts} sweeps becomes {@code UNCLASSIFIED}
     * ({@code RETRIES_EXHAUSTED}). Stops at the first deferral. Safe to run next to the consumer.
     */
    public ClassifyPendingView classifyPending() {
        SweepSummary s = pending.classifyPending();
        return new ClassifyPendingView(s.reopened(), s.picked(), s.classified(), s.unclassified(), s.skipped(),
                s.alreadyProcessed(), s.pending(), s.gaveUp(), s.failed(), s.stoppedOnDeferral());
    }

    /**
     * Messages on {@code news.raw} the news consumer has not handled yet; 0 means every published item was consumed
     * (or sent to the DLT). The job runner waits for 0 before it sweeps and exits.
     *
     * @throws IllegalStateException if the broker cannot be asked
     */
    public long consumerLag() {
        return consumerLag.lag();
    }

    /** See {@link IngestSummary} for the meaning of each count. */
    public record IngestSummaryView(List<Ticker> queried, List<Ticker> skipped, List<Ticker> failed, int fetched,
                                    int inserted, int nearDuplicates, int alreadyKnown) {}

    /** See {@link RelaySummary}. */
    public record PublishSummaryView(int published, int pending, boolean brokerUnavailable) {}

    /** See {@link SweepSummary}. */
    public record ClassifyPendingView(int reopened, int picked, int classified, int unclassified, int skipped,
                                      int alreadyProcessed, int pending, int gaveUp, int failed,
                                      boolean stoppedOnDeferral) {}
}
