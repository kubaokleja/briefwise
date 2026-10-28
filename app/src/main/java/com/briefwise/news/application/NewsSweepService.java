package com.briefwise.news.application;

import com.briefwise.news.domain.model.ProcessingOutcome;
import com.briefwise.news.domain.model.SweepPolicy;
import com.briefwise.news.domain.model.SweepSummary;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.port.NewsProcessing;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.PendingClassification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sweep of items stuck in {@code RECEIVED} (DESIGN 7.1, ADR 0016): a message that went to {@code news.raw.DLT}, an
 * outbox row published to a broker that disappeared before the consumer read it, an item deferred because the budget
 * was used up, or a {@code PROVIDER_ERROR} item reopened for another try. Each item goes through the same
 * {@link NewsProcessing} as the consumer, so the code gate, screening and budget apply unchanged.
 * <p>
 * Every pick counts one attempt before processing, so an item whose processing keeps throwing (a poison item) gives
 * up as {@code RETRIES_EXHAUSTED} instead of being swept forever. The run stops at the first deferral: the budget is
 * per feature and day, so the next items would defer too.
 */
@Service
class NewsSweepService implements PendingClassification {

    private static final Logger log = LoggerFactory.getLogger(NewsSweepService.class);

    private final NewsRepository repository;
    private final NewsProcessing processing;
    private final SweepPolicy policy;
    private final Clock clock;

    NewsSweepService(NewsRepository repository, NewsProcessing processing, SweepPolicy policy, Clock clock) {
        this.repository = repository;
        this.processing = processing;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public SweepSummary classifyPending() {
        Instant now = clock.instant();
        Instant createdBefore = now.minus(policy.minAge());
        int reopened = repository.reopenProviderErrors(createdBefore, now.minus(policy.providerErrorRetryWindow()),
                policy.maxAttempts());
        List<UUID> ids = repository.findSweepable(createdBefore, policy.batchSize());

        var counts = new Counts();
        for (UUID id : ids) {
            counts.picked++;
            if (!sweep(id, counts)) {
                counts.stoppedOnDeferral = true;
                break;
            }
        }
        SweepSummary summary = new SweepSummary(reopened, counts.picked, counts.classified, counts.unclassified,
                counts.skipped, counts.alreadyProcessed, counts.pending, counts.gaveUp, counts.failed,
                counts.stoppedOnDeferral);
        log.info("News sweep: {}", summary);
        return summary;
    }

    /** @return false if the classifier deferred and the run should stop */
    private boolean sweep(UUID id, Counts counts) {
        try {
            int attempt = repository.incrementSweepAttempts(id);
            if (attempt > policy.maxAttempts()) {
                log.warn("News item {} gives up after {} sweep attempts", id, policy.maxAttempts());
                if (repository.markUnclassifiedFromReceived(id, UnclassifiedReason.RETRIES_EXHAUSTED,
                        clock.instant())) {
                    counts.gaveUp++;
                } else {
                    counts.alreadyProcessed++;
                }
                return true;
            }
            ProcessingOutcome outcome = processing.process(id);
            switch (outcome) {
                case CLASSIFIED -> counts.classified++;
                case UNCLASSIFIED -> counts.unclassified++;
                case NOT_HELD -> counts.skipped++;
                case ALREADY_PROCESSED -> counts.alreadyProcessed++;
                case PENDING -> {
                    counts.pending++;
                    return false;
                }
            }
        } catch (RuntimeException e) {
            log.warn("News sweep failed for item {}; retried on the next run: {}", id, e.toString());
            counts.failed++;
        }
        return true;
    }

    private static final class Counts {
        int picked;
        int classified;
        int unclassified;
        int skipped;
        int alreadyProcessed;
        int pending;
        int gaveUp;
        int failed;
        boolean stoppedOnDeferral;
    }
}
