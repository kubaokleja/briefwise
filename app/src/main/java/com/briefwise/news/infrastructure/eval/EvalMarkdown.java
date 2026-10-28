package com.briefwise.news.infrastructure.eval;

import com.briefwise.news.domain.model.ClassMetrics;
import com.briefwise.news.domain.model.ConfusionMatrix;
import com.briefwise.news.domain.model.EvalReport;
import com.briefwise.news.domain.model.EvalScores;
import com.briefwise.news.domain.model.LabelScores;
import com.briefwise.news.domain.model.RegressionGate.GateResult;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code latest.md}: the eval report as GitHub markdown, posted as the PR comment by {@code evals.yml}. Summary first
 * (gate, scores on all and on random rows), then per-class tables and confusion matrices on all golden rows.
 */
final class EvalMarkdown {

    private EvalMarkdown() {}

    static String render(EvalReport report, GateResult gate) {
        StringBuilder md = new StringBuilder();
        md.append("## News classification eval\n\n");
        md.append("**Gate: ").append(gate.passed() ? "PASSED" : "FAILED").append("** - ").append(gate.message())
                .append("\n\n");
        md.append("Model `").append(report.model()).append("`, prompt `").append(report.promptVersion())
                .append("`, batch `").append(report.batchId()).append("`, ").append(report.ranAt()).append("\n\n");
        md.append(report.cases()).append(" golden cases (").append(report.randomCases())
                .append(" random), ").append(report.invalid()).append(" unusable answers (")
                .append(percent(report.invalidRate())).append(", counted as misses). Cost ")
                .append(report.costUsd().setScale(4, RoundingMode.HALF_UP)).append(" USD (batch price), ")
                .append(report.costPerThousandUsd()).append(" USD per 1,000 items.\n\n");

        md.append("| Label | Macro F1 (all) | Accuracy (all) | Macro F1 (random) | Accuracy (random) |\n");
        md.append("|---|---|---|---|---|\n");
        summaryRow(md, "Sentiment (gated)", report.all().sentiment(), report.random().sentiment());
        summaryRow(md, "Relevance", report.all().relevance(), report.random().relevance());
        summaryRow(md, "Event type", report.all().eventType(), report.random().eventType());
        md.append("\nMacro F1 averages the classes equally; accuracy is dominated by the majority class "
                + "(`NEUTRAL`, `LOW`, `OTHER`).\n");

        EvalScores all = report.all();
        details(md, "Sentiment", all.sentiment());
        details(md, "Relevance", all.relevance());
        details(md, "Event type", all.eventType());
        md.append("\n_Classes with fewer than ").append(LabelScores.MIN_RELIABLE_SUPPORT)
                .append(" cases are marked: one case moves their F1 by many points._\n");
        return md.toString();
    }

    private static void summaryRow(StringBuilder md, String name, LabelScores<?> all, LabelScores<?> random) {
        md.append("| ").append(name).append(" | ").append(FileEvalReports.format(all.macroF1())).append(" | ")
                .append(FileEvalReports.format(all.accuracy())).append(" | ")
                .append(FileEvalReports.format(random.macroF1())).append(" | ")
                .append(FileEvalReports.format(random.accuracy())).append(" |\n");
    }

    private static <E extends Enum<E>> void details(StringBuilder md, String name, LabelScores<E> scores) {
        md.append("\n<details><summary>").append(name).append(": per class and confusion matrix (all rows)")
                .append("</summary>\n\n");
        md.append("| Class | Support | Predicted | Precision | Recall | F1 |\n|---|---|---|---|---|---|\n");
        for (Map.Entry<E, ClassMetrics> e : scores.perClass().entrySet()) {
            ClassMetrics m = e.getValue();
            md.append("| ").append(e.getKey().name()).append(scores.reliable(e.getKey()) ? "" : " (too few)")
                    .append(" | ").append(m.support()).append(" | ").append(m.predicted()).append(" | ")
                    .append(FileEvalReports.format(m.precision())).append(" | ")
                    .append(FileEvalReports.format(m.recall())).append(" | ")
                    .append(FileEvalReports.format(m.f1())).append(" |\n");
        }
        matrix(md, scores.matrix());
        md.append("\n</details>\n");
    }

    /** Rows: true label; columns: predicted label, then unusable answers. Classes absent from both are left out. */
    private static <E extends Enum<E>> void matrix(StringBuilder md, ConfusionMatrix<E> matrix) {
        List<E> classes = matrix.classes().stream()
                .filter(c -> matrix.support(c) > 0 || matrix.predicted(c) > 0).toList();
        md.append("\n| true \\ predicted |");
        classes.forEach(c -> md.append(' ').append(c.name()).append(" |"));
        md.append(" invalid |\n|---|");
        classes.forEach(c -> md.append("---|"));
        md.append("---|\n");
        for (E actual : classes) {
            if (matrix.support(actual) == 0) {
                continue;
            }
            md.append("| ").append(actual.name()).append(" |");
            classes.forEach(p -> md.append(' ').append(matrix.count(actual, p)).append(" |"));
            md.append(' ').append(matrix.invalid(actual)).append(" |\n");
        }
    }

    private static String percent(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }
}
