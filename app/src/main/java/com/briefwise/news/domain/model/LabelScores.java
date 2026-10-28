package com.briefwise.news.domain.model;

import com.briefwise.news.domain.model.ConfusionMatrix.Pair;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scores of one label (sentiment, relevance or event type) over a set of cases (ADR 0018). Macro F1 is the plain mean
 * of the per-class F1 over every class that occurs in the truth or the predictions, so a rare class weighs as much as
 * a common one; accuracy is reported too but hides a model that always answers the majority class.
 */
public record LabelScores<E extends Enum<E>>(int cases, double accuracy, double macroF1, Map<E, ClassMetrics> perClass,
                                             ConfusionMatrix<E> matrix) {

    /** Below this support a class's F1 moves by many points per case; reports flag it as too few to trust. */
    public static final int MIN_RELIABLE_SUPPORT = 10;

    public LabelScores {
        Objects.requireNonNull(perClass, "perClass");
        Objects.requireNonNull(matrix, "matrix");
        perClass = Collections.unmodifiableMap(new LinkedHashMap<>(perClass));
    }

    public static <E extends Enum<E>> LabelScores<E> of(Class<E> type, List<Pair<E>> pairs) {
        ConfusionMatrix<E> matrix = ConfusionMatrix.of(type, pairs);
        Map<E, ClassMetrics> perClass = new LinkedHashMap<>();
        for (E label : matrix.classes()) {
            ClassMetrics metrics = matrix.metrics(label);
            if (metrics.support() > 0 || metrics.predicted() > 0) {
                perClass.put(label, metrics);
            }
        }
        double macroF1 = perClass.values().stream().mapToDouble(ClassMetrics::f1).average().orElse(0);
        double accuracy = matrix.total() == 0 ? 0 : (double) matrix.correct() / matrix.total();
        return new LabelScores<>(matrix.total(), accuracy, macroF1, perClass, matrix);
    }

    public boolean reliable(E label) {
        ClassMetrics metrics = perClass.get(label);
        return metrics != null && metrics.support() >= MIN_RELIABLE_SUPPORT;
    }
}
