package com.briefwise.market.infrastructure.snapshot;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the price snapshot lives and whether to load it at startup. The default directory is relative to the
 * working directory of {@code app/} (Maven, Docker Compose); override it when running from elsewhere.
 * <p>
 * The directory is bound as a {@code String}: Spring's {@code Path} conversion resolves relative values as
 * resources, which fails for {@code ../data/prices} when the app runs as a web application.
 */
@ConfigurationProperties("briefwise.market.price-snapshot")
record PriceSnapshotProperties(@DefaultValue("../data/prices") String directory,
                               @DefaultValue("false") boolean importOnStartup) {

    Path directoryPath() {
        return Path.of(directory);
    }
}
