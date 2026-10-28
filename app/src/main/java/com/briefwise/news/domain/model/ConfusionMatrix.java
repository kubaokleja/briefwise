package com.briefwise.news.domain.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Counts of (true label, predicted label) over all cases; rows are the truth, columns the prediction. An unusable
 * answer has no predicted label and is counted in its own {@code invalid} column, so every row adds up to the
 * class's support.
 */
public final class ConfusionMatrix<E extends Enum<E>> {

    private final Class<E> type;
    private final Map<E, Map<E, Integer>> counts;
    private final Map<E, Integer> invalid;

    private ConfusionMatrix(Class<E> type) {
        this.type = type;
        this.counts = new EnumMap<>(type);
        this.invalid = new EnumMap<>(type);
        for (E actual : type.getEnumConstants()) {
            Map<E, Integer> row = new EnumMap<>(type);
            for (E predicted : type.getEnumConstants()) {
                row.put(predicted, 0);
            }
            counts.put(actual, row);
            invalid.put(actual, 0);
        }
    }

    /** One pair per case: the true label and the predicted one (empty when the answer was unusable). */
    public static <E extends Enum<E>> ConfusionMatrix<E> of(Class<E> type, List<Pair<E>> pairs) {
        ConfusionMatrix<E> matrix = new ConfusionMatrix<>(type);
        for (Pair<E> pair : pairs) {
            if (pair.predicted().isPresent()) {
                matrix.counts.get(pair.actual()).merge(pair.predicted().get(), 1, Integer::sum);
            } else {
                matrix.invalid.merge(pair.actual(), 1, Integer::sum);
            }
        }
        return matrix;
    }

    public List<E> classes() {
        return List.of(type.getEnumConstants());
    }

    public int count(E actual, E predicted) {
        return counts.get(actual).get(predicted);
    }

    public int invalid(E actual) {
        return invalid.get(actual);
    }

    /** Cases whose true label is {@code actual}. */
    public int support(E actual) {
        return counts.get(actual).values().stream().mapToInt(Integer::intValue).sum() + invalid(actual);
    }

    /** Cases the model labelled {@code predicted}. */
    public int predicted(E predicted) {
        return counts.values().stream().mapToInt(row -> row.get(predicted)).sum();
    }

    public int total() {
        return classes().stream().mapToInt(this::support).sum();
    }

    public int correct() {
        return classes().stream().mapToInt(c -> count(c, c)).sum();
    }

    public ClassMetrics metrics(E label) {
        return ClassMetrics.of(count(label, label), support(label), predicted(label));
    }

    public record Pair<E extends Enum<E>>(E actual, Optional<E> predicted) {

        public Pair {
            Objects.requireNonNull(actual, "actual");
            Objects.requireNonNull(predicted, "predicted");
        }
    }
}
