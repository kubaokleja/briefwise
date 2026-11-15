package com.briefwise.research.infrastructure.snapshot;

import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.port.FilingSnapshotWriter;
import com.briefwise.shared.domain.model.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes each filing fetched from live EDGAR to the committed snapshot (ADR 0026), so the refresh shows up as a git
 * diff. Files are never deleted: a filing that leaves the selection stays, and the selection policy filters on read.
 */
@Component
@ConditionalOnProperty(prefix = "briefwise.research.filings", name = "source", havingValue = "edgar")
class JsonFilingSnapshotWriter implements FilingSnapshotWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonFilingSnapshotWriter.class);

    private final SnapshotFiles files;

    JsonFilingSnapshotWriter(FilingSnapshotProperties properties, JsonMapper json) {
        this.files = new SnapshotFiles(properties.snapshotPath(), json);
    }

    @Override
    public void write(Ticker ticker, FetchedFiling filing) {
        var target = files.path(ticker, filing.ref().accessionNo());
        files.write(SnapshotFile.of(ticker.symbol(), filing), target);
        log.info("Snapshot {} written ({} sections)", target, filing.sections().size());
    }
}
