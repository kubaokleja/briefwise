package com.briefwise.research.infrastructure.snapshot;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where filings come from (ADR 0026): {@code source} is {@code snapshot} (read {@code snapshot-dir}) or {@code edgar}
 * (live, and the snapshot is written). The directory is relative to {@code app/} and bound as a {@code String}, as for
 * the price snapshot: Spring's {@code Path} conversion would resolve {@code ../data/filings} as a resource.
 */
@ConfigurationProperties("briefwise.research.filings")
record FilingSnapshotProperties(@DefaultValue("snapshot") String source,
                                @DefaultValue("../data/filings") String snapshotDir) {

    Path snapshotPath() {
        return Path.of(snapshotDir);
    }
}
