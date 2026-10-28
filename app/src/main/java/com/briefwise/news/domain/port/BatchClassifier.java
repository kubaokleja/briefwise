package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.BatchOutcome;
import com.briefwise.news.domain.model.GoldenCase;
import java.util.List;

/**
 * Outbound port: classifies the whole golden set in one asynchronous batch with the production prompt (ADR 0018).
 * Gates (budget, few-shot leakage), logging and usage are the adapter's job, as for {@link NewsClassifier}. Never
 * throws for a refused or unfinished batch: both are a {@link BatchOutcome}.
 */
public interface BatchClassifier {

    BatchOutcome classify(List<GoldenCase> cases);
}
