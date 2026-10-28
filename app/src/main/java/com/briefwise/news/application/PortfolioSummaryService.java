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
 */
@Service
class PortfolioSummaryService implements PortfolioSummaries {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSummaryService.class);

    private final RelevantNewsQuery relevantNews;
    private final PortfolioHoldings holdings;
    private final NewsSummarizer summarizer;
    private final SummaryCompliance compliance;
    private final SummaryRepository repository;
    private final Clock clock;

    PortfolioSummaryService(RelevantNewsQuery relevantNews, PortfolioHoldings holdings, NewsSummarizer summarizer,
                            SummaryCompliance compliance, SummaryRepository repository, Clock clock) {
        this.relevantNews = relevantNews;
        this.holdings = holdings;
        this.summarizer = summarizer;
        this.compliance = compliance;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public SummaryOutcome summarize(UUID portfolioId) {
        List<RelevantNews> selection = relevantNews.relevantFor(portfolioId);
        Instant now = clock.instant();
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
        if (first instanceof SummarizerResult.Drafted(SummaryDraft draft)) {
            List<SummaryPoint> points = SummaryComposer.cite(draft, selection);
            List<SummaryViolation> violations = compliance.check(points, disclaimer);
            if (violations.isEmpty()) {
                return stored(portfolioId, today, points, draft, disclaimer);
            }
            log.info("Summary of portfolio {} breaks {} rule(s); asking again", portfolioId, violations.size());
            SummarizerResult second = summarizer.summarize(new SummaryRequest(portfolioId, selection, violations));
            if (second instanceof SummarizerResult.Drafted(SummaryDraft fixed)) {
                List<SummaryPoint> fixedPoints = SummaryComposer.cite(fixed, selection);
                if (compliance.check(fixedPoints, disclaimer).isEmpty()) {
                    return stored(portfolioId, today, fixedPoints, fixed, disclaimer);
                }
                log.warn("Summary of portfolio {} still breaks the rules after the re-ask; template used", portfolioId);
            }
        }
        return fallback(portfolioId, today, selection, disclaimer);
    }

    @Override
    public SummaryRun summarizeAll() {
        List<UUID> ids = holdings.portfolioIds();
        int generated = 0;
        int fallback = 0;
        int empty = 0;
        int unchanged = 0;
        int deferred = 0;
        int failed = 0;
        boolean stopped = false;
        for (UUID id : ids) {
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
        SummaryRun run = new SummaryRun(ids.size(), generated, fallback, empty, unchanged, deferred, failed, stopped);
        log.info("Portfolio summaries: {}", run);
        return run;
    }

    @Override
    public Optional<PortfolioSummary> latest(UUID portfolioId) {
        holdings.weights(portfolioId);
        return repository.findLatest(portfolioId);
    }

    private SummaryOutcome stored(UUID portfolioId, LocalDate today, List<SummaryPoint> points, SummaryDraft draft,
                                  String disclaimer) {
        repository.save(new PortfolioSummary(UUID.randomUUID(), portfolioId, today, clock.instant(), points, false,
                Optional.of(draft.model()), Optional.of(draft.promptVersion()), disclaimer, List.of()));
        return SummaryOutcome.GENERATED;
    }

    /** The template quotes headlines verbatim; anything it still breaks is stored with it and shown. */
    private SummaryOutcome fallback(UUID portfolioId, LocalDate today, List<RelevantNews> selection,
                                    String disclaimer) {
        List<SummaryPoint> points = SummaryComposer.template(selection);
        List<SummaryViolation> violations = compliance.check(points, disclaimer);
        repository.save(new PortfolioSummary(UUID.randomUUID(), portfolioId, today, clock.instant(), points, true,
                Optional.empty(), Optional.empty(), disclaimer, violations));
        return SummaryOutcome.FALLBACK;
    }
}
