package com.briefwise.news;

import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.news.domain.port.NewsSummarizer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the LLM summarizer in every {@code *IT} (imported by {@code AbstractIntegrationTest}, one Spring context
 * for all); {@code LlmNewsSummarizerIT} tests the real adapter directly. Records each call; by default defers, so
 * no summary is stored unless a test sets an answer.
 */
@Primary
public class RecordingNewsSummarizer implements NewsSummarizer {

    private static final Function<SummaryRequest, SummarizerResult> DEFER =
            request -> new SummarizerResult.Deferred("test");

    private final List<SummaryRequest> calls = new CopyOnWriteArrayList<>();
    private volatile Function<SummaryRequest, SummarizerResult> answer = DEFER;

    @Override
    public SummarizerResult summarize(SummaryRequest request) {
        calls.add(request);
        return answer.apply(request);
    }

    public void answer(Function<SummaryRequest, SummarizerResult> answer) {
        this.answer = answer;
    }

    public List<SummaryRequest> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
        answer = DEFER;
    }
}
