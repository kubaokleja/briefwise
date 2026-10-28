package com.briefwise.news.domain.model;

import static com.briefwise.news.domain.model.Sentiment.NEGATIVE;
import static com.briefwise.news.domain.model.Sentiment.NEUTRAL;
import static com.briefwise.news.domain.model.Sentiment.POSITIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.briefwise.news.domain.model.ConfusionMatrix.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LabelScoresTest {

    private static Pair<Sentiment> pair(Sentiment actual, Sentiment predicted) {
        return new Pair<>(actual, Optional.of(predicted));
    }

    private static Pair<Sentiment> invalid(Sentiment actual) {
        return new Pair<>(actual, Optional.empty());
    }

    /** Hand-computed: POSITIVE P 1/2 R 1/2, NEGATIVE P 1/1 R 1/2, NEUTRAL P 3/4 R 3/4 (one invalid answer). */
    private static final List<Pair<Sentiment>> EXAMPLE = List.of(
            pair(POSITIVE, POSITIVE), pair(POSITIVE, NEUTRAL),
            pair(NEGATIVE, NEGATIVE), pair(NEGATIVE, POSITIVE),
            pair(NEUTRAL, NEUTRAL), pair(NEUTRAL, NEUTRAL), pair(NEUTRAL, NEUTRAL), invalid(NEUTRAL));

    @Test
    void confusion_matrix_counts_truth_by_prediction_and_invalid_answers_apart() {
        ConfusionMatrix<Sentiment> matrix = ConfusionMatrix.of(Sentiment.class, EXAMPLE);

        assertThat(matrix.count(NEGATIVE, POSITIVE)).isEqualTo(1);
        assertThat(matrix.count(POSITIVE, NEUTRAL)).isEqualTo(1);
        assertThat(matrix.invalid(NEUTRAL)).isEqualTo(1);
        assertThat(matrix.support(NEUTRAL)).isEqualTo(4);
        assertThat(matrix.predicted(NEUTRAL)).isEqualTo(4);
        assertThat(matrix.total()).isEqualTo(8);
        assertThat(matrix.correct()).isEqualTo(5);
    }

    @Test
    void per_class_and_macro_f1_match_the_hand_computed_example() {
        LabelScores<Sentiment> scores = LabelScores.of(Sentiment.class, EXAMPLE);

        assertThat(scores.perClass().get(POSITIVE).f1()).isCloseTo(0.5, within(1e-9));
        assertThat(scores.perClass().get(NEGATIVE).precision()).isCloseTo(1.0, within(1e-9));
        assertThat(scores.perClass().get(NEGATIVE).f1()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(scores.perClass().get(NEUTRAL).f1()).isCloseTo(0.75, within(1e-9));
        assertThat(scores.macroF1()).isCloseTo((0.5 + 2.0 / 3 + 0.75) / 3, within(1e-9));
        assertThat(scores.accuracy()).isCloseTo(5.0 / 8, within(1e-9));
    }

    @Test
    void a_model_that_always_answers_the_majority_class_has_high_accuracy_but_low_macro_f1() {
        List<Pair<Sentiment>> pairs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pairs.add(pair(NEUTRAL, NEUTRAL));
        }
        pairs.add(pair(POSITIVE, NEUTRAL));
        pairs.add(pair(NEGATIVE, NEUTRAL));

        LabelScores<Sentiment> scores = LabelScores.of(Sentiment.class, pairs);

        assertThat(scores.accuracy()).isCloseTo(0.8, within(1e-9));
        assertThat(scores.perClass().get(POSITIVE).f1()).isZero();
        assertThat(scores.perClass().get(POSITIVE).precision()).isZero();
        assertThat(scores.macroF1()).isCloseTo((2 * 0.8 / 1.8) / 3, within(1e-9));
    }

    @Test
    void classes_absent_from_truth_and_predictions_are_left_out_but_a_false_alarm_class_counts() {
        LabelScores<EventType> scores = LabelScores.of(EventType.class, List.of(
                new Pair<>(EventType.EARNINGS, Optional.of(EventType.EARNINGS)),
                new Pair<>(EventType.OTHER, Optional.of(EventType.LITIGATION))));

        assertThat(scores.perClass()).containsOnlyKeys(EventType.EARNINGS, EventType.OTHER, EventType.LITIGATION);
        assertThat(scores.perClass().get(EventType.LITIGATION).support()).isZero();
        assertThat(scores.macroF1()).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    void classes_below_ten_cases_are_not_reliable() {
        LabelScores<Sentiment> scores = LabelScores.of(Sentiment.class, EXAMPLE);

        assertThat(scores.reliable(NEUTRAL)).isFalse();
        assertThat(LabelScores.of(Sentiment.class, Collections.nCopies(10, pair(NEUTRAL, NEUTRAL)))
                .reliable(NEUTRAL)).isTrue();
    }

    @Test
    void no_cases_score_zero_without_dividing_by_zero() {
        LabelScores<Sentiment> scores = LabelScores.of(Sentiment.class, List.of());

        assertThat(scores.cases()).isZero();
        assertThat(scores.accuracy()).isZero();
        assertThat(scores.macroF1()).isZero();
        assertThat(scores.perClass()).isEmpty();
    }
}
