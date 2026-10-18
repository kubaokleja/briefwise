package com.briefwise.market.infrastructure.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Regression: a relative directory must bind in a web application context (it once failed there with
 * "resource path [/../data/prices] has been normalized to [null]", which the mock-web ITs did not show).
 */
class PriceSnapshotPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(PriceSnapshotProperties.class)
    static class Config {
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void the_default_relative_directory_binds_in_a_web_context() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            PriceSnapshotProperties properties = context.getBean(PriceSnapshotProperties.class);
            assertThat(properties.directoryPath()).isEqualTo(Path.of("../data/prices"));
            assertThat(properties.importOnStartup()).isFalse();
        });
    }

    @Test
    void the_directory_can_be_overridden() {
        runner.withPropertyValues("briefwise.market.price-snapshot.directory=data/prices").run(context ->
                assertThat(context.getBean(PriceSnapshotProperties.class).directoryPath())
                        .isEqualTo(Path.of("data/prices")));
    }
}
