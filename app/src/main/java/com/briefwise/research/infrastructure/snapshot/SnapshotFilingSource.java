package com.briefwise.research.infrastructure.snapshot;

import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.FilingNotFoundException;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.port.FilingSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Filings from the committed snapshot (ADR 0026): CI, {@code demo}, {@code cloud} and the default, no network. The
 * snapshot only grows, so {@link #list} returns every stored filing of the CIK and the selection policy picks the
 * current set. A file whose sections do not match its {@code contentSha256} fails the read.
 */
@Component
@ConditionalOnProperty(prefix = "briefwise.research.filings", name = "source", havingValue = "snapshot",
        matchIfMissing = true)
class SnapshotFilingSource implements FilingSource {

    private static final Logger log = LoggerFactory.getLogger(SnapshotFilingSource.class);

    private final SnapshotFiles files;

    SnapshotFilingSource(FilingSnapshotProperties properties, JsonMapper json) {
        this.files = new SnapshotFiles(properties.snapshotPath(), json);
    }

    @Override
    public List<FilingRef> list(Cik cik) {
        List<Path> all = files.all();
        if (all.isEmpty()) {
            log.warn("No filing snapshot under {}: nothing to list for CIK {}", files.dir().toAbsolutePath(), cik);
        }
        List<FilingRef> refs = new ArrayList<>();
        for (Path file : all) {
            FilingRef ref = files.read(file).ref();
            if (ref.cik().equals(cik)) {
                refs.add(ref);
            }
        }
        return List.copyOf(refs);
    }

    @Override
    public FetchedFiling fetch(FilingRef ref) {
        Path file = files.find(ref.accessionNo())
                .orElseThrow(() -> new FilingNotFoundException("No snapshot of filing " + ref.accessionNo()));
        return files.read(file).filing();
    }
}
