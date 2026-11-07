package com.briefwise.news.application;

import com.briefwise.news.domain.model.PortfolioSummary;
import com.briefwise.news.domain.model.RegenerationGate;
import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryComposer;
import com.briefwise.news.domain.model.SummaryDraft;
import com.briefwise.news.domain.model.SummaryOutcome;
import com.briefwise.news.domain.model.SummaryPoint;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.news.domain.model.SummaryRun;
import com.briefwise.news.domain.model.SummaryViolation;
import com.briefwise.news.domain.port.GenerationAudit;
import com.briefwise.news.domain.port.NewsSummarizer;
import com.briefwise.news.domain.port.PortfolioHoldings;
import com.briefwise.news.domain.port.PortfolioSummaries;
import com.briefwise.news.domain.port.RelevantNewsQuery;
import com.briefwise.news.domain.port.SummaryCompliance;
import com.briefwise.news.domain.port.SummaryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The daily news summary per portfolio (DESIGN 7.1, ADR 0017). Selection, then the regeneration gate, so the model
 * is called only when there is something new. A draft must pass the compliance check; if it does not, the model gets
 * one re-ask that names the flagged phrases, and after that (or after invalid output or a provider error) the
 * template stands in, flagged {@code isFallback}. A deferral (budget, no model, replay miss) on the first call keeps
 * the previous summary; on the re-ask the template is stored, because a non-compliant draft must not be shown.
 * <p>
 * {@code generatedAt} is the time of the selection, before the LLM call: a HIGH item classified while the model is
 * answering is newer than the summary, so the regeneration gate picks it up on the next run. A draft that ends in the
 * template is flagged as fallback in the generation log too ({@link GenerationAudit}), so both agree.
 */
@Service
class PortfolioSummaryService implements PortfolioSummaries {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSummaryService.class);

    private final RelevantNewsQuery relevantNews;
    private final PortfolioHoldings holdings;
    private final NewsSummarizer summarizer;
    private final SummaryCompliance compliance;
    private final SummaryRepository repository;
    private final GenerationAudit audit;
    private final Clock clock;

    PortfolioSummaryService(RelevantNewsQuery relevantNews, PortfolioHoldings holdings, NewsSummarizer summarizer,
                            SummaryCompliance compliance, SummaryRepository repository, GenerationAudit audit,
                            Clock clock) {
        this.relevantNews = relevantNews;
        this.holdings = holdings;
        this.summarizer = summarizer;
        this.compliance = compliance;
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public SummaryOutcome summarize(UUID portfolioId) {
        Instant now = clock.instant();
        List<RelevantNews> selection = relevantNews.relevantFor(portfolioId);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Optional<PortfolioSummary> latest = repository.findLatest(portfolioId);
        if (!RegenerationGate.needsNewSummary(latest, selection, today)) {
            log.debug("Summary of portfolio {} is up to date; no LLM call", portfolioId);
            return SummaryOutcome.UNCHANGED;
        }
        String disclaimer = compliance.disclaimer();
        if (selection.isEmpty()) {
            repository.save(new PortfolioSummary(UUID.randomUUID(), portfolioId, today, now, List.of(), false,
                    Optional.empty(), Optional.empty(), disclaimer, List.of()));
            return SummaryOutcome.EMPTY;
        }

        SummarizerResult first = summarizer.summarize(new SummaryRequest(portfolioId, selection, List.of()));
        if (first instanceof SummarizerResult.Deferred(String reason)) {
            log.info("Summary of portfolio {} deferred: {}", portfolioId, reason);
            return SummaryOutcome.DEFERRED;
        }
        // The log entry of a draft the compliance check rejected; flagged if the template ends up standing in. A
        // re-ask that failed in the adapter flags its own entry, so it replaces this one.
        Optional<UUID> rejected = Optional.empty();
        if (first instanceof SummarizerResult.Drafted(SummaryDraft draft, Optional<UUID> draftLog)) {
            List<SummaryPoint> points = SummaryComposer.cite(draft, selection);
            List<SummaryViolation> violations = compliance.check(points, disclaimer);
            if (violations.isEmpty()) {
                return stored(portfolioId, today, now, points, draft, disclaimer);
            }
            rejected = draftLog;
            log.info("Summary of portfolio {} breaks {} rule(s); asking again", portfolioId, violations.size());
            SummarizerResult second = summarizer.summarize(new SummaryRequest(portfolioId, selection, violations));
            if (second instanceof SummarizerResult.Drafted(SummaryDraft fixed, Optional<UUID> fixedLog)) {
                List<SummaryPoint> fixedPoints = SummaryComposer.cite(fixed, selection);
                if (compliance.check(fixedPoints, disclaimer).isEmpty()) {
                    return stored(portfolioId, today, now, fixedPoints, fixed, disclaimer);
                }
                rejected = fixedLog;
                log.warn("Summary of portfolio {} still breaks the rules after the re-ask; template used", portfolioId);
            } else if (second instanceof SummarizerResult.Failed) {
                rejected = Optional.empty();
            }
        }
        SummaryOutcome outcome = fallback(portfolioId, today, now, selection, disclaimer);
        rejected.ifPresent(audit::markFallback);
        return outcome;
    }

    @Override
    public SummaryRun summarizeAll(Instant deadline) {
        List<UUID> ids = holdings.portfolioIds();
        int generated = 0;
        int fallback = 0;
        int empty = 0;
        int unchanged = 0;
        int deferred = 0;
        int failed = 0;
        boolean stopped = false;
        boolean stoppedOnDeadline = false;
        for (UUID id : ids) {
            if (!clock.instant().isBefore(deadline)) {
                stoppedOnDeadline = true;
                break;
            }
            SummaryOutcome outcome;
            try {
                outcome = summarize(id);
            } catch (RuntimeException e) {
                log.warn("Summary of portfolio {} failed; retried on the next run: {}", id, e.toString());
                failed++;
                continue;
            }
            switch (outcome) {
                case GENERATED -> generated++;
                case FALLBACK -> fallback++;
                case EMPTY -> empty++;
                case UNCHANGED -> unchanged++;
                case DEFERRED -> deferred++;
            }
            if (outcome == SummaryOutcome.DEFERRED) {
                stopped = true;
                break;
            }
        }
        SummaryRun run = new SummaryRun(ids.size(), generated, fallback, empty, unchanged, deferred, failed, stopped,
                stoppedOnDeadline);
        log.info("Portfolio summaries: {}", run);
        return run;
    }

    @Override
    public Optional<PortfolioSummary> latest(UUID portfolioId) {
        holdings.weights(portfolioId);
        return repository.findLatest(portfolioId);
    }

    private SummaryOutcome stored(UUID portfolioId, LocalDate today, Instant generatedAt, List<SummaryPoint> points,
                                  SummaryDraft draft, String disclaimer) {
        repository.save(new PortfolioSummary(UUID.randomUUID(), portfolioId, today, generatedAt, points, false,
                Optional.of(draft.model()), Optional.of(draft.promptVersion()), disclaimer, List.of()));
        return SummaryOutcome.GENERATED;
    }

    /** The template quotes headlines verbatim; anything it still breaks is stored with it and shown. */
    private SummaryOutcome fallback(UUID portfolioId, LocalDate today, Instant generatedAt,
                                    List<RelevantNews> selection, String disclaimer) {
        List<SummaryPoint> points = SummaryComposer.template(selection);
        List<SummaryViolation> violations = compliance.check(points, disclaimer);
        repository.save(new PortfolioSummary(UUID.randomUUID(), portfolioId, today, generatedAt, points, true,
                Optional.empty(), Optional.empty(), disclaimer, violations));
        return SummaryOutcome.FALLBACK;
    }
}
