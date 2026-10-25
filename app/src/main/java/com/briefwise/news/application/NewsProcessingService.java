package com.briefwise.news.application;

import com.briefwise.news.domain.event.NewsClassified;
import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.HeldInstrument;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.ProcessingOutcome;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.port.HeldInstruments;
import com.briefwise.news.domain.port.NewsClassifier;
import com.briefwise.news.domain.port.NewsProcessing;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.UntrustedContentScreen;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Handles one {@code news.raw} message (DESIGN 7.1, consumer side). Code gate first, so no LLM call is made without a
 * reason: the item is still {@code RECEIVED} (not a duplicate delivery, K3 in ADR 0011) and at least one of its
 * tickers is still held. Then injection screening (ADR 0013), then the classifier, whose adapter checks the budget
 * before each call.
 * Every result is written conditionally on {@code RECEIVED}, so of two concurrent deliveries only one counts.
 */
@Service
class NewsProcessingService implements NewsProcessing {

    private static final Logger log = LoggerFactory.getLogger(NewsProcessingService.class);

    private final NewsRepository repository;
    private final HeldInstruments held;
    private final UntrustedContentScreen screen;
    private final NewsClassifier classifier;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    NewsProcessingService(NewsRepository repository, HeldInstruments held, UntrustedContentScreen screen,
                          NewsClassifier classifier, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.held = held;
        this.screen = screen;
        this.classifier = classifier;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public ProcessingOutcome process(UUID newsId) {
        NewsItem item = repository.findById(newsId).orElseThrow(() -> new NewsItemNotFoundException(newsId));
        if (item.status() != NewsStatus.RECEIVED) {
            log.debug("News item {} is already {}; duplicate delivery ignored", newsId, item.status());
            return ProcessingOutcome.ALREADY_PROCESSED;
        }
        if (!isAnyTickerHeld(item)) {
            log.info("News item {} concerns no held ticker any more ({}); skipped without an LLM call", newsId,
                    item.tickers());
            return written(repository.skipFromReceived(newsId), newsId,
                    ProcessingOutcome.NOT_HELD);
        }
        if (screen.isSuspected(item)) {
            return markUnclassified(newsId, UnclassifiedReason.INJECTION_SUSPECTED);
        }
        return switch (classifier.classify(item)) {
            case ClassifierResult.Classified(NewsClassification classification) -> store(newsId, classification);
            case ClassifierResult.Failed(UnclassifiedReason reason) -> markUnclassified(newsId, reason);
            case ClassifierResult.Deferred(String reason) -> {
                log.info("News item {} stays RECEIVED: {}", newsId, reason);
                yield ProcessingOutcome.PENDING;
            }
        };
    }

    private boolean isAnyTickerHeld(NewsItem item) {
        Set<Ticker> heldTickers = held.list().stream().map(HeldInstrument::ticker).collect(Collectors.toSet());
        return item.tickers().stream().anyMatch(heldTickers::contains);
    }

    private ProcessingOutcome store(UUID newsId, NewsClassification classification) {
        if (!repository.classifyFromReceived(newsId, classification, clock.instant())) {
            return written(false, newsId, ProcessingOutcome.CLASSIFIED);
        }
        events.publishEvent(new NewsClassified(newsId, classification.relevance(), clock.instant()));
        return ProcessingOutcome.CLASSIFIED;
    }

    private ProcessingOutcome markUnclassified(UUID newsId, UnclassifiedReason reason) {
        log.info("News item {} is UNCLASSIFIED: {}", newsId, reason);
        return written(repository.markUnclassifiedFromReceived(newsId, reason, clock.instant()), newsId,
                ProcessingOutcome.UNCLASSIFIED);
    }

    private static ProcessingOutcome written(boolean written, UUID newsId, ProcessingOutcome outcome) {
        if (!written) {
            log.info("News item {} was processed concurrently; this result is dropped", newsId);
            return ProcessingOutcome.ALREADY_PROCESSED;
        }
        return outcome;
    }
}
