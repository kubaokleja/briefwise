package com.briefwise.news;

import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.EvalBaseline;
import com.briefwise.news.domain.model.EvalReport;
import com.briefwise.news.domain.model.EvalRun;
import com.briefwise.news.domain.model.IngestSummary;
import com.briefwise.news.domain.model.NewsFeedItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.PortfolioSummary;
import com.briefwise.news.domain.model.RegressionGate.GateResult;
import com.briefwise.news.domain.model.RelaySummary;
import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.SummaryRun;
import com.briefwise.news.domain.model.SweepSummary;
import com.briefwise.news.domain.port.ClassificationEvals;
import com.briefwise.news.domain.port.NewsConsumerLag;
import com.briefwise.news.domain.port.NewsFeed;
import com.briefwise.news.domain.port.NewsIngest;
import com.briefwise.news.domain.port.NewsProcessing;
import com.briefwise.news.domain.port.OutboxRelay;
import com.briefwise.news.domain.port.PendingClassification;
import com.briefwise.news.domain.port.PortfolioSummaries;
import com.briefwise.news.domain.port.RelevantNewsQuery;
import com.briefwise.shared.domain.model.Citation;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code news}. Methods are added with the slice that implements them;
 * no logic here, delegate and map.
 */
@Component
public class NewsFacade {

    private final NewsIngest ingest;
    private final OutboxRelay relay;
    private final NewsProcessing processing;
    private final PendingClassification pending;
    private final NewsConsumerLag consumerLag;
    private final RelevantNewsQuery relevantNews;
    private final PortfolioSummaries summaries;
    private final ClassificationEvals evals;
    private final NewsFeed feed;

    NewsFacade(NewsIngest ingest, OutboxRelay relay, NewsProcessing processing, PendingClassification pending,
               NewsConsumerLag consumerLag, RelevantNewsQuery relevantNews, PortfolioSummaries summaries,
               ClassificationEvals evals, NewsFeed feed) {
        this.ingest = ingest;
        this.relay = relay;
        this.processing = processing;
        this.pending = pending;
        this.consumerLag = consumerLag;
        this.relevantNews = relevantNews;
        this.summaries = summaries;
        this.evals = evals;
        this.feed = feed;
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
     * ({@code RETRIES_EXHAUSTED}). Stops at the first deferral, or before the next item once {@code deadline} has
     * passed. Safe to run next to the consumer.
     */
    public ClassifyPendingView classifyPending(Instant deadline) {
        SweepSummary s = pending.classifyPending(deadline);
        return new ClassifyPendingView(s.reopened(), s.picked(), s.classified(), s.unclassified(), s.skipped(),
                s.alreadyProcessed(), s.pending(), s.gaveUp(), s.failed(), s.stoppedOnDeferral(),
                s.stoppedOnDeadline());
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

    /**
     * Classified news that matters to the portfolio now (ADR 0017): HIGH or MEDIUM relevance, published in the last
     * 24 hours, at least one affected ticker held; most relevant first, then by portfolio weight; at most 15.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist
     */
    public List<RelevantNewsView> getRelevantNews(UUID portfolioId) {
        return relevantNews.relevantFor(portfolioId).stream().map(NewsFacade::toView).toList();
    }

    /**
     * The news feed (M1 slice 9): newest {@code RECEIVED}, {@code CLASSIFIED} and {@code UNCLASSIFIED} items, at most
     * {@code limit}; with a portfolio, only items fetched for one of its held tickers. Never calls the LLM.
     *
     * @throws IllegalArgumentException if {@code limit} is not between 1 and {@link #FEED_MAX_LIMIT}
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist
     */
    public List<NewsFeedItemView> getNewsFeed(Optional<UUID> portfolioId, int limit) {
        return feed.latest(portfolioId, limit).stream().map(NewsFacade::toView).toList();
    }

    /**
     * Makes the portfolio's daily news summary if the regeneration gate allows it (no summary today; today's is a
     * template or empty and news arrived; or a new HIGH item), then the compliance check, one re-ask and the
     * template fallback (ADR 0017). The LLM is called only past the gate.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist
     */
    public SummaryOutcomeView summarizeForPortfolio(UUID portfolioId) {
        return SummaryOutcomeView.valueOf(summaries.summarize(portfolioId).name());
    }

    /**
     * {@link #summarizeForPortfolio} for every portfolio; stops at the first deferral (budget used up), or before the
     * next portfolio once {@code deadline} has passed.
     */
    public SummaryRunView summarizePortfolios(Instant deadline) {
        SummaryRun r = summaries.summarizeAll(deadline);
        return new SummaryRunView(r.portfolios(), r.generated(), r.fallback(), r.empty(), r.unchanged(),
                r.deferred(), r.failed(), r.stoppedOnDeferral(), r.stoppedOnDeadline());
    }

    /**
     * The newest stored summary of the portfolio; never calls the LLM. Empty if none was made yet.
     *
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist
     */
    public Optional<PortfolioSummaryView> getLatestSummary(UUID portfolioId) {
        return summaries.latest(portfolioId).map(NewsFacade::toView);
    }

    /**
     * Classification eval (DESIGN 10, ADR 0018): the golden set in one Message Batch with the production prompt,
     * scores written to the report, then the regression gate on sentiment macro F1. Only {@code PASSED} is a pass.
     *
     * @throws IllegalStateException if the golden set is missing (not decrypted) or malformed
     */
    public ClassificationEvalView evaluateClassification() {
        EvalRun run = evals.evaluate();
        Optional<EvalReport> report = run.report();
        return new ClassificationEvalView(EvalStatusView.valueOf(run.status().name()), run.message(),
                report.map(EvalReport::cases).orElse(0), report.map(EvalReport::invalid).orElse(0),
                report.map(r -> r.all().sentiment().macroF1()),
                run.gate().flatMap(GateResult::baseline).map(EvalBaseline::sentimentMacroF1),
                report.map(EvalReport::costUsd), report.map(EvalReport::batchId));
    }

    private static RelevantNewsView toView(RelevantNews r) {
        ClassifiedNews n = r.news();
        return new RelevantNewsView(n.id(), n.headline(), n.url(), n.source(), n.publishedAt(),
                r.heldTickers().stream().sorted(Comparator.comparing(Ticker::symbol)).toList(), r.weight(),
                SentimentView.valueOf(n.sentiment().name()), RelevanceView.valueOf(n.relevance().name()),
                EventTypeView.valueOf(n.eventType().name()), n.rationale());
    }

    private static NewsFeedItemView toView(NewsFeedItem n) {
        Optional<NewsFeedItem.Labels> labels = n.labels();
        return new NewsFeedItemView(n.id(), n.headline(), n.url(), n.source(), n.publishedAt(),
                NewsStatusView.valueOf(n.status().name()),
                n.tickers().stream().sorted(Comparator.comparing(Ticker::symbol)).toList(),
                labels.map(l -> SentimentView.valueOf(l.sentiment().name())),
                labels.map(l -> RelevanceView.valueOf(l.relevance().name())),
                labels.map(l -> EventTypeView.valueOf(l.eventType().name())),
                labels.map(NewsFeedItem.Labels::rationale),
                n.unclassifiedReason().map(r -> UnclassifiedReasonView.valueOf(r.name())));
    }

    private static PortfolioSummaryView toView(PortfolioSummary s) {
        return new PortfolioSummaryView(s.portfolioId(), s.summaryDate(), s.generatedAt(),
                s.points().stream().map(p -> new SummaryPointView(p.text(), p.citations())).toList(),
                s.disclaimer(), !s.fallback() && !s.empty(), s.fallback(), s.empty(), s.model(), s.promptVersion(),
                s.violations().stream().map(v -> new SummaryViolationView(v.rule(), v.offendingText())).toList());
    }

    /** See {@link IngestSummary} for the meaning of each count. */
    public record IngestSummaryView(List<Ticker> queried, List<Ticker> skipped, List<Ticker> failed, int fetched,
                                    int inserted, int nearDuplicates, int alreadyKnown) {}

    /** See {@link RelaySummary}. */
    public record PublishSummaryView(int published, int pending, boolean brokerUnavailable) {}

    /** See {@link SweepSummary}. */
    public record ClassifyPendingView(int reopened, int picked, int classified, int unclassified, int skipped,
                                      int alreadyProcessed, int pending, int gaveUp, int failed,
                                      boolean stoppedOnDeferral, boolean stoppedOnDeadline) {}

    public enum SentimentView { NEGATIVE, NEUTRAL, POSITIVE }

    public enum RelevanceView { LOW, MEDIUM, HIGH }

    public enum EventTypeView {
        EARNINGS, GUIDANCE, M_AND_A, REGULATORY, LITIGATION, MANAGEMENT, MACRO, PRODUCT, ANALYST, OTHER
    }

    /** {@code heldTickers}: the item's affected tickers the portfolio holds; {@code weight}: the largest of theirs. */
    public record RelevantNewsView(UUID id, String headline, String url, String source, Instant publishedAt,
                                   List<Ticker> heldTickers, BigDecimal weight, SentimentView sentiment,
                                   RelevanceView relevance, EventTypeView eventType, String rationale) {}

    /** Largest {@code limit} of {@link #getNewsFeed}. */
    public static final int FEED_MAX_LIMIT = NewsFeed.MAX_LIMIT;

    /** Feed statuses; {@code SKIPPED} near-duplicates are never in the feed. */
    public enum NewsStatusView { RECEIVED, CLASSIFIED, UNCLASSIFIED }

    /** See {@link com.briefwise.news.domain.model.UnclassifiedReason}. */
    public enum UnclassifiedReasonView { INVALID_OUTPUT, PROVIDER_ERROR, INJECTION_SUSPECTED, RETRIES_EXHAUSTED }

    /**
     * Labels are present only for {@code CLASSIFIED}; {@code unclassifiedReason} only for {@code UNCLASSIFIED}.
     * {@code tickers}: every ticker the item was fetched for.
     */
    public record NewsFeedItemView(UUID id, String headline, String url, String source, Instant publishedAt,
                                   NewsStatusView status, List<Ticker> tickers, Optional<SentimentView> sentiment,
                                   Optional<RelevanceView> relevance, Optional<EventTypeView> eventType,
                                   Optional<String> rationale, Optional<UnclassifiedReasonView> unclassifiedReason) {}

    /** What {@link #summarizeForPortfolio} did. */
    public enum SummaryOutcomeView { GENERATED, FALLBACK, EMPTY, UNCHANGED, DEFERRED }

    /** See {@link SummaryRun}. */
    public record SummaryRunView(int portfolios, int generated, int fallback, int empty, int unchanged, int deferred,
                                 int failed, boolean stoppedOnDeferral, boolean stoppedOnDeadline) {}

    /**
     * {@code aiGenerated}: a model wrote the points (show the AI marker); false for a template {@code fallback}
     * ("built from a template, AI unavailable") and for an {@code empty} summary. {@code violations}: rules a
     * template still breaks (e.g. a quoted headline).
     */
    public record PortfolioSummaryView(UUID portfolioId, LocalDate summaryDate, Instant generatedAt,
                                       List<SummaryPointView> points, String disclaimer, boolean aiGenerated,
                                       boolean fallback, boolean empty, Optional<String> model,
                                       Optional<String> promptVersion, List<SummaryViolationView> violations) {}

    public record SummaryPointView(String text, List<Citation> citations) {}

    public record SummaryViolationView(String rule, String offendingText) {}

    /** See {@link EvalRun}: only {@code PASSED} is a pass. */
    public enum EvalStatusView { PASSED, REGRESSED, REFUSED, INCOMPLETE }

    /** Scores are empty when nothing was measured (refused or incomplete batch). */
    public record ClassificationEvalView(EvalStatusView status, String message, int cases, int invalid,
                                         Optional<Double> sentimentMacroF1, Optional<Double> baselineSentimentMacroF1,
                                         Optional<BigDecimal> costUsd, Optional<String> batchId) {

        public boolean passed() {
            return status == EvalStatusView.PASSED;
        }
    }
}
