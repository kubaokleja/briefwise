package com.briefwise.news.infrastructure.eval;

import com.briefwise.news.domain.model.ClassMetrics;
import com.briefwise.news.domain.model.ConfusionMatrix;
import com.briefwise.news.domain.model.EvalBaseline;
import com.briefwise.news.domain.model.EvalReport;
import com.briefwise.news.domain.model.EvalScores;
import com.briefwise.news.domain.model.LabelScores;
import com.briefwise.news.domain.model.RegressionGate.GateResult;
import com.briefwise.news.domain.port.EvalReports;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code latest.json} (every metric, for tools and as the next baseline) and {@code latest.md} (the PR
 * comment) to {@code results-dir}, and reads {@code baseline.json}, a committed copy of an earlier {@code latest.json}
 * (ADR 0018). Metrics and counts only, never news text: the golden set's text may not be published.
 */
@Component
class FileEvalReports implements EvalReports {

    static final String LATEST_JSON = "latest.json";
    static final String LATEST_MD = "latest.md";
    static final String BASELINE_JSON = "baseline.json";

    private final Path dir;
    private final JsonMapper json;

    FileEvalReports(NewsEvalProperties properties, JsonMapper json) {
        this.dir = properties.resultsDir();
        this.json = json;
    }

    @Override
    public Optional<EvalBaseline> baseline() {
        Path file = dir.resolve(BASELINE_JSON);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = json.readTree(Files.readString(file, StandardCharsets.UTF_8));
            JsonNode f1 = root.path("all").path("sentiment").path("macroF1");
            if (!f1.isNumber()) {
                throw new IllegalStateException(file + " has no all.sentiment.macroF1");
            }
            return Optional.of(new EvalBaseline(root.path("model").asString(), root.path("promptVersion").asString(),
                    f1.asDouble()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    @Override
    public void write(EvalReport report, GateResult gate) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(LATEST_JSON),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(report, gate)) + "\n",
                    StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(LATEST_MD), EvalMarkdown.render(report, gate), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the eval report to " + dir, e);
        }
    }

    static Map<String, Object> toJson(EvalReport report, GateResult gate) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", report.model());
        root.put("promptVersion", report.promptVersion());
        root.put("batchId", report.batchId());
        root.put("ranAt", report.ranAt().toString());
        root.put("cases", report.cases());
        root.put("randomCases", report.randomCases());
        root.put("invalid", report.invalid());
        root.put("invalidRate", round(report.invalidRate()));
        root.put("costUsd", report.costUsd().setScale(6, RoundingMode.HALF_UP));
        root.put("costPer1000Usd", report.costPerThousandUsd());
        Map<String, Object> gateJson = new LinkedHashMap<>();
        gateJson.put("passed", gate.passed());
        gateJson.put("message", gate.message());
        gate.baseline().ifPresent(b -> {
            gateJson.put("baselineModel", b.model());
            gateJson.put("baselinePromptVersion", b.promptVersion());
            gateJson.put("baselineSentimentMacroF1", round(b.sentimentMacroF1()));
        });
        root.put("gate", gateJson);
        root.put("all", scores(report.all()));
        root.put("random", scores(report.random()));
        return root;
    }

    private static Map<String, Object> scores(EvalScores scores) {
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("sentiment", label(scores.sentiment()));
        labels.put("relevance", label(scores.relevance()));
        labels.put("eventType", label(scores.eventType()));
        return labels;
    }

    private static <E extends Enum<E>> Map<String, Object> label(LabelScores<E> scores) {
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("cases", scores.cases());
        label.put("macroF1", round(scores.macroF1()));
        label.put("accuracy", round(scores.accuracy()));
        Map<String, Object> perClass = new LinkedHashMap<>();
        scores.perClass().forEach((cls, m) -> perClass.put(cls.name(), classJson(m, scores.reliable(cls))));
        label.put("perClass", perClass);
        label.put("confusion", confusion(scores.matrix()));
        return label;
    }

    private static Map<String, Object> classJson(ClassMetrics m, boolean reliable) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("support", m.support());
        json.put("predicted", m.predicted());
        json.put("precision", round(m.precision()));
        json.put("recall", round(m.recall()));
        json.put("f1", round(m.f1()));
        json.put("reliable", reliable);
        return json;
    }

    /** Rows are the true label, columns the prediction, plus {@code INVALID}; classes without cases are left out. */
    private static <E extends Enum<E>> Map<String, Object> confusion(ConfusionMatrix<E> matrix) {
        Map<String, Object> rows = new LinkedHashMap<>();
        for (E actual : matrix.classes()) {
            if (matrix.support(actual) == 0) {
                continue;
            }
            Map<String, Integer> row = new LinkedHashMap<>();
            for (E predicted : matrix.classes()) {
                if (matrix.count(actual, predicted) > 0) {
                    row.put(predicted.name(), matrix.count(actual, predicted));
                }
            }
            if (matrix.invalid(actual) > 0) {
                row.put("INVALID", matrix.invalid(actual));
            }
            rows.put(actual.name(), row);
        }
        return rows;
    }

    static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
