package com.briefwise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Snapshot of the API contract. The UI generates its TypeScript types from docs/openapi.json (ADR 0007), so the
 * committed file must match what the controllers produce. On a difference the test rewrites the file and fails:
 * review the diff, run {@code npm run gen:api} in ui/, and commit both.
 */
class OpenApiSpecIT extends AbstractIntegrationTest {

    /** Maven runs tests with app/ as the working directory. */
    private static final Path SPEC = Path.of("..", "docs", "openapi.json");

    @Autowired
    MockMvc mvc;

    @Test
    void committed_spec_matches_the_controllers() throws Exception {
        String generated = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip() + "\n";

        // Git may check the file out with CRLF on Windows; compare content, not line endings.
        String committed = Files.exists(SPEC)
                ? Files.readString(SPEC, StandardCharsets.UTF_8).replace("\r\n", "\n")
                : "";

        if (!generated.equals(committed)) {
            Files.writeString(SPEC, generated, StandardCharsets.UTF_8);
        }
        assertThat(committed)
                .as("docs/openapi.json was out of date and has been rewritten; review it, run `npm run gen:api` "
                        + "in ui/ and commit both")
                .isEqualTo(generated);
    }
}
