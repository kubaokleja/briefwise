package com.briefwise.news.domain.model;

import com.briefwise.news.domain.model.ConfusionMatrix.Pair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Scores of all three labels over one set of cases (all golden rows, or the random rows alone). */
public record EvalScores(LabelScores<Sentiment> sentiment, LabelScores<Relevance> relevance,
                         LabelScores<EventType> eventType) {

    /** A case without a prediction (missing from the batch results) counts as an unusable answer. */
    public static EvalScores of(List<GoldenCase> cases, Map<UUID, EvalPrediction> predictions) {
        return new EvalScores(
                LabelScores.of(Sentiment.class, pairs(cases, predictions, EvalLabels::sentiment)),
                LabelScores.of(Relevance.class, pairs(cases, predictions, EvalLabels::relevance)),
                LabelScores.of(EventType.class, pairs(cases, predictions, EvalLabels::eventType)));
    }

    private static <E extends Enum<E>> List<Pair<E>> pairs(List<GoldenCase> cases,
                                                           Map<UUID, EvalPrediction> predictions,
                                                           Function<EvalLabels, E> label) {
        return cases.stream()
                .map(c -> new Pair<>(label.apply(c.expected()),
                        Optional.ofNullable(predictions.get(c.id())).flatMap(EvalPrediction::labels).map(label)))
                .toList();
    }
}
