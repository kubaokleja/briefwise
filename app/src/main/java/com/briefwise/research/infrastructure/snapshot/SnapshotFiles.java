package com.briefwise.research.infrastructure.snapshot;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes {@code {dir}/{TICKER}/{accession}.json}. Output is pretty-printed with LF line endings on every
 * OS and ends with a newline, so a refresh that changes nothing changes no byte in git.
 */
final class SnapshotFiles {

    private static final String SUFFIX = ".json";

    private final Path dir;
    private final ObjectWriter writer;
    private final ObjectReader reader;

    SnapshotFiles(Path dir, JsonMapper json) {
        this.dir = dir;
        DefaultIndenter lf = new DefaultIndenter("  ", "\n");
        this.writer = json.writer().with(new DefaultPrettyPrinter().withObjectIndenter(lf).withArrayIndenter(lf));
        this.reader = json.readerFor(SnapshotFile.class);
    }

    Path dir() {
        return dir;
    }

    Path path(Ticker ticker, AccessionNo accessionNo) {
        return dir.resolve(ticker.symbol()).resolve(accessionNo.value() + SUFFIX);
    }

    /** Through a temporary file and a move, so a crash never leaves half a file in the snapshot. */
    void write(SnapshotFile file, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temp, writer.writeValueAsString(file) + "\n", StandardCharsets.UTF_8);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the filing snapshot " + target, e);
        }
    }

    SnapshotFile read(Path file) {
        try {
            return reader.readValue(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the filing snapshot " + file, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Malformed filing snapshot " + file + ": " + e.getMessage(), e);
        }
    }

    /** Every {@code *.json} one level below the ticker folders, sorted; empty if the directory does not exist. */
    List<Path> all() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> tickers = Files.list(dir)) {
            for (Path tickerDir : tickers.filter(Files::isDirectory).toList()) {
                try (Stream<Path> entries = Files.list(tickerDir)) {
                    entries.filter(f -> f.getFileName().toString().endsWith(SUFFIX) && Files.isRegularFile(f))
                            .forEach(files::add);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the filing snapshot " + dir, e);
        }
        files.sort(null);
        return List.copyOf(files);
    }

    /** The file of an accession under any ticker folder. */
    Optional<Path> find(AccessionNo accessionNo) {
        String name = accessionNo.value() + SUFFIX;
        return all().stream().filter(f -> f.getFileName().toString().equals(name)).findFirst();
    }
}
