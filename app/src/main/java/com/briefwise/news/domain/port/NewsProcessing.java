package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.NewsItemNotFoundException;
import com.briefwise.news.domain.model.ProcessingOutcome;
import java.util.UUID;

/** Inbound port: handle one ingested item (a {@code news.raw} message). Implemented by a package-private service. */
public interface NewsProcessing {

    /**
     * Idempotent (K3, ADR 0011): only an item still {@code RECEIVED} is processed, and its new status is written
     * only if it is still {@code RECEIVED}, so a duplicate delivery changes nothing.
     *
     * @throws NewsItemNotFoundException if no item has this id
     */
    ProcessingOutcome process(UUID newsId);
}
