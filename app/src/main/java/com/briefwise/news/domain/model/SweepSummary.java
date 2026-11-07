package com.briefwise.news.domain.model;

/**
 * Result of one sweep run (ADR 0016). {@code reopened}: {@code PROVIDER_ERROR} items put back to {@code RECEIVED};
 * {@code picked}: items the sweep took up. Per picked item one of: {@code classified}, {@code unclassified},
 * {@code skipped} (no ticker held any more), {@code alreadyProcessed} (a consumer was faster), {@code pending} (the
 * classifier deferred, e.g. budget used up), {@code gaveUp} ({@code RETRIES_EXHAUSTED}) or {@code failed} (an
 * exception; retried on the next run). {@code stoppedOnDeferral}: the run stopped at the first deferral, since the
 * rest would defer too. {@code stoppedOnDeadline}: the run stopped before the next item because the job's
 * {@code max-duration} was reached; the rest stays {@code RECEIVED} for the next run.
 */
public record SweepSummary(int reopened, int picked, int classified, int unclassified, int skipped,
                           int alreadyProcessed, int pending, int gaveUp, int failed, boolean stoppedOnDeferral,
                           boolean stoppedOnDeadline) {}
