package com.briefwise.aiops.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.TokenCount;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NewGenerationTest {

    @Test
    void blank_prompt_version_is_rejected_because_every_call_must_be_traceable_to_a_prompt() {
        assertThatThrownBy(() -> new NewGeneration("news-classification", "claude-haiku-5-5", " ", "prompt",
                Optional.empty(), new TokenCount(1, 1), Duration.ZERO, false, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptVersion");
    }

    @Test
    void negative_latency_is_rejected() {
        assertThatThrownBy(() -> new NewGeneration("news-classification", "claude-haiku-5-5", "v1", "prompt",
                Optional.empty(), new TokenCount(1, 1), Duration.ofMillis(-1), false, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
