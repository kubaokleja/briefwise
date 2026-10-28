package com.briefwise.news.application;

import com.briefwise.news.domain.model.BatchOutcome;
import com.briefwise.news.domain.model.EvalReport;
import com.briefwise.news.domain.model.EvalRun;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.model.RegressionGate;
import com.briefwise.news.domain.model.RegressionGate.GateResult;
import com.briefwise.news.domain.port.BatchClassifier;
import com.briefwise.news.domain.port.ClassificationEvals;
import com.briefwise.news.domain.port.EvalReports;
import com.briefwise.news.domain.port.GoldenSet;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The classification eval (DESIGN 10, ADR 0018): golden set -> one batch with the production prompt -> scores on
 * all and on random rows -> report -> regression gate on sentiment macro F1. Nothing is written for a refused or
 * unfinished batch; such a run is not a pass.
 */
@Service
class ClassificationEvalService implements ClassificationEvals {

    private static final Logger log = LoggerFactory.getLogger(ClassificationEvalService.class);

    private final GoldenSet goldenSet;
    private final BatchClassifier classifier;
    private final EvalReports reports;
    private final RegressionGate gate;
    private final Clock clock;

    ClassificationEvalService(GoldenSet goldenSet, BatchClassifier classifier, EvalReports reports,
                              RegressionGate gate, Clock clock) {
        this.goldenSet = goldenSet;
        this.classifier = classifier;
        this.reports = reports;
        this.gate = gate;
        this.clock = clock;
    }

    @Override
    public EvalRun evaluate() {
        List<GoldenCase> cases = goldenSet.load();
        if (cases.isEmpty()) {
            throw new IllegalStateException("The golden set is empty");
        }
        log.info("Classification eval: {} golden cases", cases.size());
        return switch (classifier.classify(cases)) {
            case BatchOutcome.Refused refused -> {
                log.warn("Classification eval refused: {}", refused.reason());
                yield new EvalRun(EvalRun.Status.REFUSED, Optional.empty(), Optional.empty(), refused.reason());
            }
            case BatchOutcome.Incomplete incomplete -> {
                log.warn("Classification eval batch {} did not end: {}", incomplete.batchId(), incomplete.reason());
                yield new EvalRun(EvalRun.Status.INCOMPLETE, Optional.empty(), Optional.empty(),
                        "batch " + incomplete.batchId() + ": " + incomplete.reason());
            }
            case BatchOutcome.Completed completed -> scored(cases, completed);
        };
    }

    private EvalRun scored(List<GoldenCase> cases, BatchOutcome.Completed batch) {
        EvalReport report = EvalReport.of(cases, batch, clock.instant());
        GateResult result = gate.check(report.all().sentiment().macroF1(), reports.baseline());
        reports.write(report, result);
        log.info("Classification eval {}: {} ({} of {} answers invalid, {} USD)",
                result.passed() ? "passed" : "REGRESSED", result.message(), report.invalid(), report.cases(),
                report.costUsd());
        return new EvalRun(result.passed() ? EvalRun.Status.PASSED : EvalRun.Status.REGRESSED, Optional.of(report),
                Optional.of(result), result.message());
    }
}
