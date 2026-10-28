package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.EvalBaseline;
import com.briefwise.news.domain.model.EvalReport;
import com.briefwise.news.domain.model.RegressionGate.GateResult;
import java.util.Optional;

/** Outbound port: the committed baseline and the report of the latest run (metrics only, ADR 0018). */
public interface EvalReports {

    /** Empty before the first baseline is committed. */
    Optional<EvalBaseline> baseline();

    void write(EvalReport report, GateResult gate);
}
