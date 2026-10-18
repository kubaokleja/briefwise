package com.briefwise.market.infrastructure.snapshot;

import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.port.PriceSnapshotSource;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Reads {@code <TICKER>.csv} files with the header {@code date,close,volume} (written by {@code quant/}). */
@Component
class CsvPriceSnapshotSource implements PriceSnapshotSource {

    private static final String HEADER = "date,close,volume";

    private final Path directory;

    CsvPriceSnapshotSource(PriceSnapshotProperties properties) {
        this.directory = properties.directoryPath();
    }

    @Override
    public Map<Ticker, List<DailyPrice>> read() {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "Price snapshot directory not found: " + directory.toAbsolutePath().normalize());
        }
        Map<Ticker, List<DailyPrice>> snapshot = new LinkedHashMap<>();
        for (Path file : csvFiles()) {
            String name = file.getFileName().toString();
            snapshot.put(new Ticker(name.substring(0, name.length() - ".csv".length())), parse(file));
        }
        return snapshot;
    }

    private List<Path> csvFiles() {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".csv")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list price snapshot directory " + directory, e);
        }
    }

    private static List<DailyPrice> parse(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst().trim())) {
            throw new IllegalStateException(file.getFileName() + ": expected header '" + HEADER + "'");
        }
        List<DailyPrice> prices = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                prices.add(parseRow(lines.get(i)));
            } catch (RuntimeException e) {
                throw new IllegalStateException(file.getFileName() + ":" + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return prices;
    }

    private static DailyPrice parseRow(String line) {
        String[] parts = line.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("expected 3 columns but found " + parts.length);
        }
        return new DailyPrice(LocalDate.parse(parts[0]), new BigDecimal(parts[1]), Long.parseLong(parts[2]));
    }
}
