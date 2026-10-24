package com.briefwise.news.application;

import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.ProcessingOutcome;
import com.briefwise.news.domain.port.NewsClassifier;
import com.briefwise.news.domain.port.NewsProcessing;
import com.briefwise.news.domain.port.NewsRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles one {@code news.raw} message (DESIGN 7.1, consumer side). Idempotency is the item status (K3, ADR 0011):
 * a delivery for an item that is no longer {@code RECEIVED} does nothing, and the result is written conditionally,
 * so of two concurrent deliveries only one changes the item. The code gate, screening, generation log and the
 * classification fields come with the LLM adapter in M1 slice 4.
 */
@Service
class NewsProcessingService implements NewsProcessing {

    private static final Logger log = LoggerFactory.getLogger(NewsProcessingService.class);

    private final NewsRepository repository;
    private final NewsClassifier classifier;

    NewsProcessingService(NewsRepository repository, NewsClassifier classifier) {
        this.repository = repository;
        this.classifier = classifier;
    }

    @Override
    public ProcessingOutcome process(UUID newsId) {
        NewsItem item = repository.findById(newsId).orElseThrow(() -> new NewsItemNotFoundException(newsId));
        if (item.status() != NewsStatus.RECEIVED) {
            log.debug("News item {} is already {}; duplicate delivery ignored", newsId, item.status());
            return ProcessingOutcome.ALREADY_PROCESSED;
        }
        Optional<NewsClassification> classification = classifier.classify(item);
        if (classification.isEmpty()) {
            return ProcessingOutcome.PENDING;
        }
        if (!repository.transitionFromReceived(newsId, NewsStatus.CLASSIFIED)) {
            log.info("News item {} was processed concurrently; this result is dropped", newsId);
            return ProcessingOutcome.ALREADY_PROCESSED;
        }
        return ProcessingOutcome.CLASSIFIED;
    }
}
