package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.port.NewsClassifier;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in until the LLM adapter (M1 slice 4): never classifies, so every item stays {@code RECEIVED} and will be
 * classified once the real adapter replaces this class. No LLM call, no cost.
 */
@Component
class PlaceholderNewsClassifier implements NewsClassifier {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderNewsClassifier.class);

    @Override
    public Optional<NewsClassification> classify(NewsItem item) {
        log.debug("No classifier yet; news item {} stays RECEIVED", item.id());
        return Optional.empty();
    }
}
