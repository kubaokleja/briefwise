package com.briefwise.market.domain.port;

import com.briefwise.market.domain.model.ImportSummary;

/** Inbound port: load the committed price snapshot (ADR 0004) into storage. Idempotent. */
public interface PriceSnapshotImport {

    /**
     * Stores every close that is not stored yet; existing closes are left untouched.
     *
     * @throws IllegalStateException if the snapshot is missing or malformed
     */
    ImportSummary importSnapshot();
}
