package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Citation;
import java.util.List;
import java.util.Objects;

/** One statement of a portfolio summary and the news items it rests on ("every claim has a source"). */
public record SummaryPoint(String text, List<Citation> citations) {

    public SummaryPoint {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A summary point needs text");
        }
        citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
    }
}
