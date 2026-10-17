package com.briefwise;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.shared.infrastructure.config.BriefwiseProperties;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.LlmProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** With no profile selected the app runs as `local` (Ollama provider). Deliberately without @ActiveProfiles. */
@SpringBootTest
@Import(TestcontainersConfig.class)
class DefaultProfileIT {

    @Autowired
    Environment environment;

    @Autowired
    BriefwiseProperties properties;

    @Test
    void local_is_the_default_profile() {
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).containsExactly("local");
        assertThat(properties.llm().provider()).isEqualTo(LlmProvider.OLLAMA);
    }
}
