package com.briefwise.aiops.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ReplayFixtureStoreTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ReplayKey KEY = ReplayKey.of("news-classification", "claude-haiku-5-5", "v1", "input");

    @TempDir
    Path dir;

    private static String line(ReplayKey key, String response) {
        return JSON.writeValueAsString(Map.of("key", key.hash(), "feature", "news-classification",
                "model", "claude-haiku-5-5", "prompt_version", "v1", "response", response,
                "recorded_at", "2026-10-27T08:00:00Z"));
    }

    @Test
    void jsonl_lines_become_entries_and_blank_lines_and_other_files_are_ignored() throws IOException {
        Files.writeString(dir.resolve("news-classification.jsonl"), line(KEY, "{\"sentiment\":\"POSITIVE\"}") + "\n\n");
        Files.writeString(dir.resolve("README.md"), "not a fixture");

        Map<ReplayKey, ReplayEntry> entries = ReplayFixtureStore.load(dir, JSON);

        assertThat(entries).containsOnlyKeys(KEY);
        assertThat(entries.get(KEY)).isEqualTo(new ReplayEntry(KEY, "claude-haiku-5-5",
                "{\"sentiment\":\"POSITIVE\"}", Instant.parse("2026-10-27T08:00:00Z")));
    }

    @Test
    void missing_directory_means_no_fixtures() {
        assertThat(ReplayFixtureStore.load(dir.resolve("absent"), JSON)).isEmpty();
    }

    @Test
    void a_key_that_appears_twice_fails_startup_instead_of_picking_one() throws IOException {
        Files.writeString(dir.resolve("a.jsonl"), line(KEY, "{\"a\":1}") + "\n");
        Files.writeString(dir.resolve("b.jsonl"), line(KEY, "{\"b\":2}") + "\n");

        assertThatThrownBy(() -> ReplayFixtureStore.load(dir, JSON)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appears twice");
    }

    @Test
    void a_malformed_line_fails_startup_with_file_and_line() throws IOException {
        Files.writeString(dir.resolve("bad.jsonl"), line(KEY, "{}") + "\n{\"key\":\"" + KEY.hash() + "\"}\n");

        assertThatThrownBy(() -> ReplayFixtureStore.load(dir, JSON)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad.jsonl:2").hasMessageContaining("model");
    }
}
