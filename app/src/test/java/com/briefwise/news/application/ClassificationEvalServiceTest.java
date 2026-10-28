package com.briefwise.news.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.news.domain.model.BatchOutcome;
import com.briefwise.news.domain.model.EvalBaseline;
import com.briefwise.news.domain.model.EvalLabels;
import com.briefwise.news.domain.model.EvalPrediction;
import com.briefwise.news.domain.model.EvalRun;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.model.RegressionGate;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.port.BatchClassifier;
import com.briefwise.news.domain.port.EvalReports;
import com.briefwise.news.domain.port.GoldenSet;
import com.briefwise.shared.domain.model.Ticker;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassificationEvalServiceTest {

    private static final TokenCount TOKENS = new TokenCount(100, 50);

    private final GoldenSet goldenSet = mock(GoldenSet.class);
    private final BatchClassifier classifier = mock(BatchClassifier.class);
    private final EvalReports reports = mock(EvalReports.class);
    private final ClassificationEvalService service = new ClassificationEvalService(goldenSet, classifier, reports,
            new RegressionGate(3.0), Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

    private final GoldenCase positive = golden(Sentiment.POSITIVE);
    private final GoldenCase negative = golden(Sentiment.NEGATIVE);
    private final GoldenCase neutral = golden(Sentiment.NEUTRAL);

    private static GoldenCase golden(Sentiment sentiment) {
        return new GoldenCase(UUID.randomUUID(), true, Set.of(new Ticker("AAPL")), "Wire", "Headline", "",
                new EvalLabels(sentiment, Relevance.LOW, EventType.OTHER));
    }

    private static EvalPrediction right(GoldenCase c) {
        return new EvalPrediction(c.id(), Optional.of(c.expected()), TOKENS);
    }

    private BatchOutcome.Completed batch(EvalPrediction... predictions) {
        return new BatchOutcome.Completed("msgbatch_1", "claude-haiku-5-5", "v1", List.of(predictions),
                new BigDecimal("0.001"));
    }

    @Test
    void a_run_without_a_baseline_passes_and_is_written() {
        when(goldenSet.load()).thenReturn(List.of(positive, negative, neutral));
        when(classifier.classify(any())).thenReturn(batch(right(positive), right(negative), right(neutral)));
        when(reports.baseline()).thenReturn(Optional.empty());

        EvalRun run = service.evaluate();

        assertThat(run.status()).isEqualTo(EvalRun.Status.PASSED);
        assertThat(run.report().orElseThrow().all().sentiment().macroF1()).isEqualTo(1.0);
        verify(reports).write(any(), any());
    }

    @Test
    void a_drop_beyond_the_gate_is_a_regression_but_the_report_is_still_written() {
        when(goldenSet.load()).thenReturn(List.of(positive, negative, neutral));
        EvalPrediction wrong = new EvalPrediction(negative.id(),
                Optional.of(new EvalLabels(Sentiment.NEUTRAL, Relevance.LOW, EventType.OTHER)), TOKENS);
        when(classifier.classify(any())).thenReturn(batch(right(positive), wrong, right(neutral)));
        when(reports.baseline()).thenReturn(Optional.of(new EvalBaseline("claude-haiku-5-5", "v1", 1.0)));

        EvalRun run = service.evaluate();

        assertThat(run.status()).isEqualTo(EvalRun.Status.REGRESSED);
        assertThat(run.passed()).isFalse();
        assertThat(run.message()).contains("baseline 1.000");
        verify(reports).write(any(), any());
    }

    @Test
    void invalid_answers_count_as_misses_not_as_skipped_cases() {
        when(goldenSet.load()).thenReturn(List.of(positive, negative, neutral));
        when(classifier.classify(any())).thenReturn(batch(right(positive), EvalPrediction.invalid(negative.id(),
                TOKENS), right(neutral)));
        when(reports.baseline()).thenReturn(Optional.empty());

        EvalRun run = service.evaluate();

        assertThat(run.report().orElseThrow().invalid()).isEqualTo(1);
        assertThat(run.report().orElseThrow().all().sentiment().cases()).isEqualTo(3);
        assertThat(run.report().orElseThrow().all().sentiment().macroF1()).isLessThan(1.0);
    }

    @Test
    void a_refused_batch_is_not_a_pass_and_writes_nothing() {
        when(goldenSet.load()).thenReturn(List.of(positive));
        when(classifier.classify(any())).thenReturn(new BatchOutcome.Refused("daily budget is used up"));

        EvalRun run = service.evaluate();

        assertThat(run.status()).isEqualTo(EvalRun.Status.REFUSED);
        assertThat(run.message()).contains("budget");
        assertThat(run.report()).isEmpty();
        verify(reports, never()).write(any(), any());
    }

    @Test
    void an_unfinished_batch_is_not_a_pass_and_names_the_batch() {
        when(goldenSet.load()).thenReturn(List.of(positive));
        when(classifier.classify(any())).thenReturn(new BatchOutcome.Incomplete("msgbatch_9", "not ended"));

        EvalRun run = service.evaluate();

        assertThat(run.status()).isEqualTo(EvalRun.Status.INCOMPLETE);
        assertThat(run.message()).contains("msgbatch_9");
        verify(reports, never()).write(any(), any());
    }

    @Test
    void an_empty_golden_set_is_an_error() {
        when(goldenSet.load()).thenReturn(List.of());

        assertThatThrownBy(service::evaluate).isInstanceOf(IllegalStateException.class);
        verify(classifier, never()).classify(any());
    }
}
