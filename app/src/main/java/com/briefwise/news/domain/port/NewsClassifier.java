package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import java.util.Optional;

/**
 * Outbound port: classify one item. Empty means "no result", and the item stays {@code RECEIVED}. Until M1 slice 4
 * the only adapter is a placeholder that always returns empty; slice 4 adds the LLM adapter and its fallback.
 */
public interface NewsClassifier {

    Optional<NewsClassification> classify(NewsItem item);
}
