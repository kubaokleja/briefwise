package com.briefwise.news.infrastructure.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.briefwise.news.domain.model.BatchOutcome;
import com.briefwise.news.domain.model.EvalBaseline;
import com.briefwise.news.domain.model.EvalLabels;
import com.briefwise.news.domain.model.EvalPrediction;
import com.briefwise.news.domain.model.EvalReport;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.model.RegressionGate;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.Ticker;
import com.briefwise.shared.domain.model.TokenCount;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FileEvalReportsTest {

    @TempDir
    Path dir;

    private FileEvalReports reports() {
        return new FileEvalReports(new NewsEvalProperties(dir.resolve("golden.csv"), dir.resolve("results"), 3.0),
                JsonMapper.builder().build());
    }

    private static EvalReport report() {
        GoldenCase positive = new GoldenCase(UUID.randomUUID(), true, Set.of(new Ticker("AAPL")), "Wire",
                "Secret headline text", "Secret summary", new EvalLabels(Sentiment.POSITIVE, Relevance.HIGH,
                EventType.EARNINGS));
        GoldenCase neutral = new GoldenCase(UUID.randomUUID(), false, Set.of(new Ticker("MSFT")), "Wire",
                "Another headline", "", new EvalLabels(Sentiment.NEUTRAL, Relevance.LOW, EventType.OTHER));
        BatchOutcome.Completed batch = new BatchOutcome.Completed("msgbatch_1", "claude-haiku-5-5", "v1", List.of(
                new EvalPrediction(positive.id(), Optional.of(positive.expected()), new TokenCount(100, 50)),
                EvalPrediction.invalid(neutral.id(), new TokenCount(100, 50))), new BigDecimal("0.0004"));
        return EvalReport.of(List.of(positive, neutral), batch, Instant.parse("2026-10-28T08:00:00Z"));
    }

    @Test
    void without_a_baseline_file_there_is_no_baseline() {
        assertThat(reports().baseline()).isEmpty();
    }

    @Test
    void latest_json_copied_to_baseline_json_is_read_back_as_the_baseline() throws IOException {
        FileEvalReports reports = reports();
        EvalReport report = report();
        reports.write(report, new RegressionGate(3.0).check(report.all().sentiment().macroF1(), Optional.empty()));

        Path results = dir.resolve("results");
        Files.copy(results.resolve("latest.json"), results.resolve("baseline.json"));

        EvalBaseline baseline = reports.baseline().orElseThrow();
        assertThat(baseline.model()).isEqualTo("claude-haiku-5-5");
        assertThat(baseline.promptVersion()).isEqualTo("v1");
        assertThat(baseline.sentimentMacroF1()).isCloseTo(report.all().sentiment().macroF1(), within(1e-4));
    }

    @Test
    void reports_hold_metrics_but_never_news_text() throws IOException {
        EvalReport report = report();
        reports().write(report, new RegressionGate(3.0).check(0.5,
                Optional.of(new EvalBaseline("claude-haiku-5-5", "v1", 0.9))));

        String json = Files.readString(dir.resolve("results/latest.json"), StandardCharsets.UTF_8);
        String md = Files.readString(dir.resolve("results/latest.md"), StandardCharsets.UTF_8);

        assertThat(json).contains("\"macroF1\"").contains("\"INVALID\" : 1").contains("\"passed\" : false")
                .doesNotContain("Secret").doesNotContain("headline text");
        assertThat(md).contains("**Gate: FAILED**").contains("| Sentiment (gated) |").contains("(too few)")
                .contains("1 unusable answers").doesNotContain("Secret");
    }
}
