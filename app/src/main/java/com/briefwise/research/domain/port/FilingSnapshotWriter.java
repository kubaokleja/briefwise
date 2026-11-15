package com.briefwise.research.domain.port;

import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.shared.domain.model.Ticker;

/** Outbound port: keeps a fetched filing in the committed text snapshot (ADR 0026). A no-op for the snapshot source. */
public interface FilingSnapshotWriter {

    void write(Ticker ticker, FetchedFiling filing);
}
