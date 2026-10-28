package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryRequest;

/**
 * Outbound port: summarise the selected news of one portfolio with an LLM. Like {@link NewsClassifier}, the adapter
 * checks the budget before each call, logs every call and records its usage, re-asks once after invalid output and
 * does not throw for invalid output or provider errors. Call it only after the regeneration gate.
 */
public interface NewsSummarizer {

    SummarizerResult summarize(SummaryRequest request);
}
