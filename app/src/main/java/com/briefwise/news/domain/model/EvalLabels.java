package com.briefwise.news.domain.model;

import java.util.Objects;

/** The three labels of a classification, as the golden set holds them and the eval compares them (ADR 0018). */
public record EvalLabels(Sentiment sentiment, Relevance relevance, EventType eventType) {

    public EvalLabels {
        Objects.requireNonNull(sentiment, "sentiment");
        Objects.requireNonNull(relevance, "relevance");
        Objects.requireNonNull(eventType, "eventType");
    }
}
