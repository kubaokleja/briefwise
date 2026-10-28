package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * A classified item selected for one portfolio. {@code heldTickers}: the item's affected tickers the portfolio holds.
 * {@code weight}: the largest portfolio weight among them, used to order items of equal relevance (ADR 0014: the
 * classifier rates relevance per item, weighting by portfolio happens here, in code).
 */
public record RelevantNews(ClassifiedNews news, Set<Ticker> heldTickers, BigDecimal weight) {

    public RelevantNews {
        Objects.requireNonNull(news, "news");
        heldTickers = Set.copyOf(Objects.requireNonNull(heldTickers, "heldTickers"));
        if (heldTickers.isEmpty()) {
            throw new IllegalArgumentException("A relevant item concerns at least one held ticker");
        }
        Objects.requireNonNull(weight, "weight");
    }
}
