package com.briefwise.news.domain.model;

import static com.briefwise.news.SummaryFixtures.NOW;
import static com.briefwise.news.SummaryFixtures.classified;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SelectionPolicyTest {

    private final SelectionPolicy policy = new SelectionPolicy(Duration.ofHours(24), 3, Relevance.MEDIUM);

    private final Map<Ticker, BigDecimal> weights = Map.of(
            new Ticker("AAPL"), new BigDecimal("0.10"),
            new Ticker("MSFT"), new BigDecimal("0.30"),
            new Ticker("NVDA"), new BigDecimal("0.05"));

    @Test
    void qualifying_relevances_are_the_minimum_and_above() {
        assertThat(policy.relevances()).containsExactlyInAnyOrder(Relevance.MEDIUM, Relevance.HIGH);
    }

    @Test
    void orders_by_relevance_then_by_held_weight() {
        ClassifiedNews mediumHeavy = classified("MSFT medium", Relevance.MEDIUM, NOW, "MSFT");
        ClassifiedNews highLight = classified("NVDA high", Relevance.HIGH, NOW, "NVDA");
        ClassifiedNews highHeavier = classified("AAPL high", Relevance.HIGH, NOW, "AAPL");

        List<RelevantNews> selected = policy.select(List.of(mediumHeavy, highLight, highHeavier), weights);

        assertThat(selected).extracting(r -> r.news().headline())
                .containsExactly("AAPL high", "NVDA high", "MSFT medium");
    }

    @Test
    void weight_is_the_largest_among_the_held_affected_tickers_and_unheld_tickers_are_dropped() {
        ClassifiedNews multi = classified("Deal", Relevance.HIGH, NOW, "AAPL", "MSFT", "GOOGL");

        RelevantNews selected = policy.select(List.of(multi), weights).getFirst();

        assertThat(selected.heldTickers()).containsExactlyInAnyOrder(new Ticker("AAPL"), new Ticker("MSFT"));
        assertThat(selected.weight()).isEqualByComparingTo("0.30");
    }

    @Test
    void items_without_a_held_ticker_or_below_the_minimum_are_dropped() {
        ClassifiedNews notHeld = classified("Google", Relevance.HIGH, NOW, "GOOGL");
        ClassifiedNews low = classified("Listicle", Relevance.LOW, NOW, "AAPL");

        assertThat(policy.select(List.of(notHeld, low), weights)).isEmpty();
    }

    @Test
    void cuts_at_max_items_after_ordering() {
        List<ClassifiedNews> many = List.of(
                classified("m1", Relevance.MEDIUM, NOW, "AAPL"),
                classified("m2", Relevance.MEDIUM, NOW, "AAPL"),
                classified("h1", Relevance.HIGH, NOW, "NVDA"),
                classified("m3", Relevance.MEDIUM, NOW, "AAPL"));

        List<RelevantNews> selected = policy.select(many, weights);

        assertThat(selected).hasSize(3);
        assertThat(selected.getFirst().news().headline()).isEqualTo("h1");
    }

    @Test
    void rejects_a_non_positive_window_or_cap() {
        assertThatThrownBy(() -> new SelectionPolicy(Duration.ZERO, 3, Relevance.MEDIUM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectionPolicy(Duration.ofHours(1), 0, Relevance.MEDIUM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
