package com.briefwise;

import com.briefwise.news.RecordingNewsClassifier;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for all {@code *IT} tests. Identical annotations let Spring reuse one application context (and one
 * Postgres and Kafka container) across test classes. Requires Docker. The news classifier is a recording fake
 * ({@link RecordingNewsClassifier}) for the same reason: overriding it per class would mean per-class contexts.
 * <p>
 * One WireMock server stands in for every external HTTP API (Finnhub, Anthropic) and is shared for the same reason:
 * per-class servers would mean per-class contexts. Tests that stub it reset it first. It stops with the JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingNewsClassifier.class})
public abstract class AbstractIntegrationTest {

    protected static final WireMockServer WIREMOCK = startWireMock();

    @DynamicPropertySource
    static void externalApis(DynamicPropertyRegistry registry) {
        registry.add("briefwise.news.finnhub.base-url", WIREMOCK::baseUrl);
        registry.add("spring.ai.anthropic.base-url", WIREMOCK::baseUrl);
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }
}
