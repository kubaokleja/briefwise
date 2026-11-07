package com.briefwise.news.domain.model;

/**
 * Result of summarising every portfolio. {@code failed}: portfolios whose summary threw (retried on the next run).
 * {@code stoppedOnDeferral}: the run stopped at the first deferral, because the budget is per feature and day.
 * {@code stoppedOnDeadline}: the run stopped before the next portfolio because the job's {@code max-duration} was
 * reached; those portfolios keep their previous summary.
 */
public record SummaryRun(int portfolios, int generated, int fallback, int empty, int unchanged, int deferred,
                         int failed, boolean stoppedOnDeferral, boolean stoppedOnDeadline) {
}
