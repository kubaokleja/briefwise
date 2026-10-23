package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Small value objects of the news context: dedup and ingest policies, classification. */
class PoliciesTest {

    private static final Duration WINDOW = Duration.ofHours(48);

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.01, Double.NaN})
    void similarity_threshold_must_be_in_zero_one(double threshold) {
        assertThatThrownBy(() -> new DedupPolicy(threshold, WINDOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void window_must_be_positive() {
        assertThatThrownBy(() -> new DedupPolicy(0.6, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DedupPolicy(1.0, WINDOW).headlineSimilarity()).isEqualTo(1.0);
    }

    @Test
    void lookback_must_not_be_negative() {
        DedupPolicy dedup = new DedupPolicy(0.6, WINDOW);
        assertThatThrownBy(() -> new IngestPolicy(-1, dedup)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new IngestPolicy(0, dedup).lookbackDays()).isZero();
    }

    @Test
    void classification_needs_rationale_and_prompt_version() {
        Set<Ticker> tickers = Set.of(new Ticker("AAPL"));
        assertThatThrownBy(() -> new NewsClassification(Sentiment.POSITIVE, Relevance.HIGH, EventType.EARNINGS,
                tickers, " ", "v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewsClassification(Sentiment.POSITIVE, Relevance.HIGH, EventType.EARNINGS,
                tickers, "beat estimates", "")).isInstanceOf(IllegalArgumentException.class);
    }
}
