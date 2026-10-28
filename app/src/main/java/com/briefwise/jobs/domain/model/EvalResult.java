package com.briefwise.jobs.domain.model;

import java.util.Map;

/**
 * One eval run as the jobs see it (ADR 0018): whether it passed its gate, why, and the figures to report. A run that
 * measured nothing (refused, unfinished batch) has not passed.
 */
public record EvalResult(boolean passed, String message, Map<String, Object> figures) {

    public EvalResult {
        figures = Map.copyOf(figures);
    }
}
