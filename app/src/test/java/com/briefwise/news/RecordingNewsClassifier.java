package com.briefwise.news;

import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.port.NewsClassifier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the LLM classifier in every {@code *IT} (imported by {@code AbstractIntegrationTest}, so all tests keep
 * one Spring context); {@code LlmNewsClassifierIT} tests the real adapter directly. Records each call; by default
 * defers, so items stay {@code RECEIVED}. Called from the Kafka listener thread, hence the thread-safe fields.
 */
@Primary
public class RecordingNewsClassifier implements NewsClassifier {

    private final List<NewsItem> calls = new CopyOnWriteArrayList<>();
    private static final Function<NewsItem, ClassifierResult> DEFER = item -> new ClassifierResult.Deferred("test");

    private volatile Function<NewsItem, ClassifierResult> answer = DEFER;

    @Override
    public ClassifierResult classify(NewsItem item) {
        calls.add(item);
        return answer.apply(item);
    }

    public void answer(Function<NewsItem, ClassifierResult> answer) {
        this.answer = answer;
    }

    public List<NewsItem> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
        answer = DEFER;
    }
}
