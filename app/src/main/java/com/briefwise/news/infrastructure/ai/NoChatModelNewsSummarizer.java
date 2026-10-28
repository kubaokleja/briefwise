package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.news.domain.port.NewsSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Active when no chat model is configured: never calls an LLM, so the previous summary stays. No cost. */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none", matchIfMissing = true)
class NoChatModelNewsSummarizer implements NewsSummarizer {

    private static final Logger log = LoggerFactory.getLogger(NoChatModelNewsSummarizer.class);

    @Override
    public SummarizerResult summarize(SummaryRequest request) {
        log.debug("No chat model configured; no summary for portfolio {}", request.portfolioId());
        return new SummarizerResult.Deferred("no chat model configured");
    }
}
