package com.briefwise.research.infrastructure.snapshot;

import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.port.FilingSnapshotWriter;
import com.briefwise.shared.domain.model.Ticker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** With the snapshot as source, the filing already is the snapshot: nothing to write. */
@Component
@ConditionalOnProperty(prefix = "briefwise.research.filings", name = "source", havingValue = "snapshot",
        matchIfMissing = true)
class NoOpFilingSnapshotWriter implements FilingSnapshotWriter {

    @Override
    public void write(Ticker ticker, FetchedFiling filing) {
        // intentionally empty
    }
}
