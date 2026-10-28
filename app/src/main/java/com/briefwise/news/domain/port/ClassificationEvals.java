package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.EvalRun;

/** Inbound port: run the classification eval against the golden set and gate on the baseline (ADR 0018). */
public interface ClassificationEvals {

    /** @throws IllegalStateException if the golden set is missing or malformed */
    EvalRun evaluate();
}
