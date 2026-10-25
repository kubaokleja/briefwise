package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.port.NewsClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Active when no chat model is configured ({@code spring.ai.model.chat=none}, e.g. the {@code demo} profile until the
 * replay provider in M1 slice 5): never calls an LLM, so every item stays {@code RECEIVED} for a later run. No cost.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none", matchIfMissing = true)
class NoChatModelNewsClassifier implements NewsClassifier {

    private static final Logger log = LoggerFactory.getLogger(NoChatModelNewsClassifier.class);

    @Override
    public ClassifierResult classify(NewsItem item) {
        log.debug("No chat model configured; news item {} stays RECEIVED", item.id());
        return new ClassifierResult.Deferred("no chat model configured");
    }
}
