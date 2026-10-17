package com.briefwise.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.shared.infrastructure.config.BriefwiseProperties.ExecutionMode;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.LlmProvider;
import com.briefwise.shared.infrastructure.config.BriefwiseProperties.ReplayOnMiss;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class BriefwisePropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BriefwiseProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void binds_execution_jobs_and_llm_from_kebab_case_yaml_style_properties() {
        runner.withPropertyValues(
                        "briefwise.execution.mode=on-demand",
                        "briefwise.jobs.news-ingest.enabled=true",
                        "briefwise.jobs.news-ingest.cron=0 */15 7-22 * * MON-FRI",
                        "briefwise.jobs.brief.enabled=false",
                        "briefwise.llm.provider=replay",
                        "briefwise.llm.replay.on-miss=fail")
                .run(context -> {
                    BriefwiseProperties props = context.getBean(BriefwiseProperties.class);
                    assertThat(props.execution().mode()).isEqualTo(ExecutionMode.ON_DEMAND);
                    assertThat(props.jobs()).containsOnlyKeys("news-ingest", "brief");
                    assertThat(props.jobs().get("news-ingest").enabled()).isTrue();
                    assertThat(props.jobs().get("news-ingest").cron()).isEqualTo("0 */15 7-22 * * MON-FRI");
                    assertThat(props.jobs().get("brief").enabled()).isFalse();
                    assertThat(props.llm().provider()).isEqualTo(LlmProvider.REPLAY);
                    assertThat(props.llm().replay().onMiss()).isEqualTo(ReplayOnMiss.FAIL);
                });
    }

    @Test
    void cors_defaults_to_the_vite_dev_server() {
        runner.withPropertyValues("briefwise.execution.mode=scheduled", "briefwise.llm.provider=ollama")
                .run(context -> assertThat(context.getBean(BriefwiseProperties.class).cors().allowedOrigins())
                        .containsExactly("http://localhost:5173"));
    }

    @Test
    void unknown_llm_provider_fails_startup() {
        runner.withPropertyValues("briefwise.execution.mode=on-demand", "briefwise.llm.provider=gpt")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missing_llm_provider_fails_startup() {
        runner.withPropertyValues("briefwise.execution.mode=on-demand")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missing_execution_mode_fails_startup() {
        runner.withPropertyValues("briefwise.llm.provider=ollama")
                .run(context -> assertThat(context).hasFailed());
    }
}
