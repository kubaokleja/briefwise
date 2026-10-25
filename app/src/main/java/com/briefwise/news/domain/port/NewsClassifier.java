package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.NewsItem;

/**
 * Outbound port: classify one item with an LLM. The adapter checks the budget before each call, logs every call and
 * records its usage, and turns invalid output or provider errors into {@link ClassifierResult.Failed} after one
 * retry, so it does not throw for those. Call it only after the code gate and screening passed.
 */
public interface NewsClassifier {

    ClassifierResult classify(NewsItem item);
}
