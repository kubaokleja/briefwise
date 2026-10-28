package com.briefwise.news.domain.model;

import static com.briefwise.news.SummaryFixtures.NOW;
import static com.briefwise.news.SummaryFixtures.relevant;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegenerationGateTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-28");
    private static final Instant MADE = NOW.minusSeconds(600);

    private static Optional<PortfolioSummary> summary(LocalDate date, boolean fallback, boolean empty) {
        List<SummaryPoint> points = empty ? List.of() : List.of(new SummaryPoint("Apple beat estimates.", List.of()));
        return Optional.of(new PortfolioSummary(UUID.randomUUID(), UUID.randomUUID(), date, MADE, points, fallback,
                fallback || empty ? Optional.empty() : Optional.of("claude-haiku-5-5"),
                fallback || empty ? Optional.empty() : Optional.of("v1"), "Disclaimer.", List.of()));
    }

    private final List<RelevantNews> oldHigh = List.of(relevant("old", Relevance.HIGH, MADE.minusSeconds(60),
            "AAPL", "0.1"));

    @Test
    void no_summary_yet_or_one_from_another_day_means_a_new_one() {
        assertThat(RegenerationGate.needsNewSummary(Optional.empty(), List.of(), TODAY)).isTrue();
        assertThat(RegenerationGate.needsNewSummary(summary(TODAY.minusDays(1), false, false), oldHigh, TODAY))
                .isTrue();
    }

    @Test
    void todays_summary_stays_without_new_high_news() {
        List<RelevantNews> newMedium = List.of(oldHigh.getFirst(),
                relevant("new medium", Relevance.MEDIUM, MADE.plusSeconds(60), "MSFT", "0.2"));

        assertThat(RegenerationGate.needsNewSummary(summary(TODAY, false, false), newMedium, TODAY)).isFalse();
    }

    @Test
    void a_high_item_classified_after_todays_summary_means_a_new_one() {
        List<RelevantNews> newHigh = List.of(relevant("new high", Relevance.HIGH, MADE.plusSeconds(60), "AAPL",
                "0.1"));

        assertThat(RegenerationGate.needsNewSummary(summary(TODAY, false, false), newHigh, TODAY)).isTrue();
    }

    @Test
    void todays_fallback_or_empty_summary_is_retried_once_there_is_news() {
        assertThat(RegenerationGate.needsNewSummary(summary(TODAY, true, false), oldHigh, TODAY)).isTrue();
        assertThat(RegenerationGate.needsNewSummary(summary(TODAY, false, true), oldHigh, TODAY)).isTrue();
    }

    @Test
    void todays_summary_stays_when_nothing_is_selected() {
        assertThat(RegenerationGate.needsNewSummary(summary(TODAY, true, false), List.of(), TODAY)).isFalse();
        assertThat(RegenerationGate.needsNewSummary(summary(TODAY, false, true), List.of(), TODAY)).isFalse();
    }
}
