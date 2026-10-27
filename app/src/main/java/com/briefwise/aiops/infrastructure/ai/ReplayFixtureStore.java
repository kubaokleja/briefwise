package com.briefwise.aiops.infrastructure.ai;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import com.briefwise.aiops.domain.port.ReplayStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Committed replay fixtures ({@code evals/replay/*.jsonl}, exported from {@code ai_generation_log}), read once at
 * startup and asked before the database. One JSON object per line with {@code key}, {@code model}, {@code response}
 * and {@code recorded_at}; other fields ({@code feature}, {@code prompt_version}, {@code subject_id}) are for humans.
 * A missing directory means no fixtures. A malformed line or a key that appears twice fails startup: fixtures are
 * curated, and a silent pick between two answers would make CI depend on file order.
 */
@Component
@Order(1)
class ReplayFixtureStore implements ReplayStore {

    private static final Logger log = LoggerFactory.getLogger(ReplayFixtureStore.class);

    private final Map<ReplayKey, ReplayEntry> entries;

    ReplayFixtureStore(LlmProperties properties, JsonMapper json) {
        this.entries = load(properties.replay().fixturesDir(), json);
    }

    @Override
    public Optional<ReplayEntry> find(ReplayKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    int size() {
        return entries.size();
    }

    static Map<ReplayKey, ReplayEntry> load(Path dir, JsonMapper json) {
        if (!Files.isDirectory(dir)) {
            log.info("No replay fixtures directory at {}", dir.toAbsolutePath().normalize());
            return Map.of();
        }
        Map<ReplayKey, ReplayEntry> entries = new HashMap<>();
        for (Path file : jsonlFiles(dir)) {
            List<String> lines = readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                ReplayEntry entry = parse(lines.get(i), json, file, i + 1);
                if (entries.putIfAbsent(entry.key(), entry) != null) {
                    throw new IllegalStateException("Replay key " + entry.key() + " appears twice (" + file + ":"
                            + (i + 1) + ")");
                }
            }
        }
        log.info("Loaded {} replay fixtures from {}", entries.size(), dir.toAbsolutePath().normalize());
        return Map.copyOf(entries);
    }

    private static ReplayEntry parse(String line, JsonMapper json, Path file, int lineNumber) {
        try {
            JsonNode node = json.readTree(line);
            return new ReplayEntry(new ReplayKey(text(node, "key")), text(node, "model"), text(node, "response"),
                    Instant.parse(text(node, "recorded_at")));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unreadable replay fixture " + file + ":" + lineNumber + ": "
                    + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("missing text field '" + field + "'");
        }
        return value.asString();
    }

    private static List<Path> jsonlFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".jsonl")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + dir, e);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }
}
