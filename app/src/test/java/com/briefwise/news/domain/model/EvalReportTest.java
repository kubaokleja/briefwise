package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.briefwise.shared.domain.model.Ticker;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvalReportTest {

    private static final Instant NOW = Instant.parse("2026-10-28T08:00:00Z");
    private static final TokenCount TOKENS = new TokenCount(100, 50);

    private static GoldenCase golden(boolean random, Sentiment sentiment) {
        return new GoldenCase(UUID.randomUUID(), random, Set.of(new Ticker("AAPL")), "Wire", "Headline", "",
                new EvalLabels(sentiment, Relevance.LOW, EventType.OTHER));
    }

    private static EvalPrediction answer(GoldenCase c, Sentiment sentiment) {
        return new EvalPrediction(c.id(), Optional.of(new EvalLabels(sentiment, Relevance.LOW, EventType.OTHER)),
                TOKENS);
    }

    @Test
    void unusable_and_missing_answers_count_as_invalid_misses() {
        GoldenCase answered = golden(true, Sentiment.POSITIVE);
        GoldenCase malformed = golden(true, Sentiment.NEGATIVE);
        GoldenCase missing = golden(false, Sentiment.NEUTRAL);
        BatchOutcome.Completed batch = new BatchOutcome.Completed("msgbatch_1", "claude-haiku-5-5", "v1",
                List.of(answer(answered, Sentiment.POSITIVE), EvalPrediction.invalid(malformed.id(), TOKENS)),
                new BigDecimal("0.003"));

        EvalReport report = EvalReport.of(List.of(answered, malformed, missing), batch, NOW);

        assertThat(report.cases()).isEqualTo(3);
        assertThat(report.invalid()).isEqualTo(2);
        assertThat(report.invalidRate()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(report.all().sentiment().accuracy()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(report.all().sentiment().matrix().invalid(Sentiment.NEUTRAL)).isEqualTo(1);
    }

    @Test
    void random_rows_are_scored_on_their_own() {
        GoldenCase random = golden(true, Sentiment.POSITIVE);
        GoldenCase company = golden(false, Sentiment.NEGATIVE);
        BatchOutcome.Completed batch = new BatchOutcome.Completed("msgbatch_1", "claude-haiku-5-5", "v1",
                List.of(answer(random, Sentiment.POSITIVE), answer(company, Sentiment.POSITIVE)), BigDecimal.ONE);

        EvalReport report = EvalReport.of(List.of(random, company), batch, NOW);

        assertThat(report.randomCases()).isEqualTo(1);
        assertThat(report.random().sentiment().cases()).isEqualTo(1);
        assertThat(report.random().sentiment().accuracy()).isEqualTo(1.0);
        assertThat(report.all().sentiment().accuracy()).isEqualTo(0.5);
    }

    @Test
    void cost_per_thousand_items_scales_the_run_cost() {
        List<GoldenCase> cases = List.of(golden(true, Sentiment.NEUTRAL), golden(true, Sentiment.NEUTRAL));
        BatchOutcome.Completed batch = new BatchOutcome.Completed("msgbatch_1", "claude-haiku-5-5", "v1",
                List.of(), new BigDecimal("0.0005"));

        assertThat(EvalReport.of(cases, batch, NOW).costPerThousandUsd()).isEqualByComparingTo("0.25");
    }
}
