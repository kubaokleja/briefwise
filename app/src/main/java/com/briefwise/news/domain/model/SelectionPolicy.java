package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which classified news a portfolio sees (getRelevantNews, ADR 0017): items published within {@code window} with
 * relevance at least {@code minRelevance} and at least one affected ticker the portfolio holds; most relevant first,
 * then by the largest held weight, then newest; at most {@code maxItems}. The window also keeps stale items (e.g.
 * classified days late after an outage) out of today's summary.
 */
public record SelectionPolicy(Duration window, int maxItems, Relevance minRelevance) {

    private static final Comparator<RelevantNews> ORDER = Comparator
            .comparing((RelevantNews r) -> r.news().relevance()).reversed()
            .thenComparing(RelevantNews::weight, Comparator.reverseOrder())
            .thenComparing((RelevantNews r) -> r.news().publishedAt(), Comparator.reverseOrder())
            .thenComparing(r -> r.news().id());

    public SelectionPolicy {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
        if (maxItems < 1) {
            throw new IllegalArgumentException("maxItems must be at least 1, was " + maxItems);
        }
        Objects.requireNonNull(minRelevance, "minRelevance");
    }

    /** The relevance levels that qualify, e.g. MEDIUM and HIGH. */
    public Set<Relevance> relevances() {
        return Arrays.stream(Relevance.values()).filter(r -> r.compareTo(minRelevance) >= 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Orders and caps the candidates. Candidates without a held affected ticker or below {@code minRelevance} are
     * dropped, so the repository query may be broader than the policy.
     *
     * @param weights portfolio weight per held ticker
     */
    public List<RelevantNews> select(List<ClassifiedNews> candidates, Map<Ticker, BigDecimal> weights) {
        return candidates.stream()
                .filter(n -> n.relevance().compareTo(minRelevance) >= 0)
                .map(n -> toRelevant(n, weights))
                .flatMap(Optional::stream)
                .sorted(ORDER)
                .limit(maxItems)
                .toList();
    }

    private static Optional<RelevantNews> toRelevant(ClassifiedNews news, Map<Ticker, BigDecimal> weights) {
        Set<Ticker> held = news.affectedTickers().stream().filter(weights::containsKey)
                .collect(Collectors.toUnmodifiableSet());
        if (held.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal weight = held.stream().map(weights::get).max(Comparator.naturalOrder()).orElseThrow();
        return Optional.of(new RelevantNews(news, held, weight));
    }
}
