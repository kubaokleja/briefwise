package com.briefwise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code job} (and {@code cloud}) profile switches logback to one JSON object per line. "test" comes last so
 * the replay LLM provider and the Testcontainers database still apply; {@code job} makes the app non-web.
 */
@SpringBootTest
@ActiveProfiles({"job", "test"})
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class JsonLoggingIT {

    private static final Logger log = LoggerFactory.getLogger(JsonLoggingIT.class);

    @Test
    void job_profile_logs_json_with_the_correlation_id(CapturedOutput output) throws Exception {
        MDC.put("correlationId", "job-42");
        try {
            log.info("hello from the job profile");
        } finally {
            MDC.remove("correlationId");
        }

        String line = output.getAll().lines()
                .filter(l -> l.contains("hello from the job profile"))
                .findFirst()
                .orElseThrow();
        JsonNode json = JsonMapper.builder().build().readTree(line);

        assertThat(json.get("message").asText()).isEqualTo("hello from the job profile");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("app").asText()).isEqualTo("briefwise");
        assertThat(json.get("correlationId").asText()).isEqualTo("job-42");
    }
}
