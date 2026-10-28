package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One hand-labelled news item of the golden set (DESIGN 10, ADR 0018): the fields the classification prompt sees and
 * the expected labels. {@code random} rows come from the random export and show the real class mix; the others were
 * oversampled from company headlines, so metrics are also reported on the random rows alone.
 */
public record GoldenCase(UUID id, boolean random, Set<Ticker> tickers, String source, String headline, String summary,
                         EvalLabels expected) {

    public GoldenCase {
        Objects.requireNonNull(id, "id");
        tickers = Set.copyOf(Objects.requireNonNull(tickers, "tickers"));
        if (tickers.isEmpty()) {
            throw new IllegalArgumentException("A golden case concerns at least one ticker");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(headline, "headline");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(expected, "expected");
    }
}
