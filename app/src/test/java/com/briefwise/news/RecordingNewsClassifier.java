package com.briefwise.news;

import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.port.NewsClassifier;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the placeholder classifier in every {@code *IT} (imported by {@code AbstractIntegrationTest}, so all
 * tests keep one Spring context). Records each call; by default answers like the placeholder (empty). Called from the
 * Kafka listener thread, hence the thread-safe fields.
 */
@Primary
public class RecordingNewsClassifier implements NewsClassifier {

    private final List<NewsItem> calls = new CopyOnWriteArrayList<>();
    private volatile Function<NewsItem, Optional<NewsClassification>> answer = item -> Optional.empty();

    @Override
    public Optional<NewsClassification> classify(NewsItem item) {
        calls.add(item);
        return answer.apply(item);
    }

    public void answer(Function<NewsItem, Optional<NewsClassification>> answer) {
        this.answer = answer;
    }

    public List<NewsItem> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
        answer = item -> Optional.empty();
    }
}
