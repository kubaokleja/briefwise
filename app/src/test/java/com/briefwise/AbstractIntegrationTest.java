package com.briefwise;

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
 * Postgres container) across test classes. Requires Docker.
 * <p>
 * One WireMock server stands in for every external HTTP API (Finnhub so far) and is shared for the same reason:
 * per-class servers would mean per-class contexts. Tests that stub it reset it first. It stops with the JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final WireMockServer WIREMOCK = startWireMock();

    @DynamicPropertySource
    static void externalApis(DynamicPropertyRegistry registry) {
        registry.add("briefwise.news.finnhub.base-url", WIREMOCK::baseUrl);
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }
}
