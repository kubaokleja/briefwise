package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.IngestSummary;

/** Inbound port: fetch news for held tickers, deduplicate and store it. Implemented by a package-private service. */
public interface NewsIngest {

    /**
     * Safe to repeat: URLs already stored are not stored again. A ticker whose source call fails is reported in
     * {@link IngestSummary#failed()} and does not stop the run.
     *
     * @throws IllegalStateException if the news source is misconfigured (e.g. no or a rejected API key)
     */
    IngestSummary ingest();
}
