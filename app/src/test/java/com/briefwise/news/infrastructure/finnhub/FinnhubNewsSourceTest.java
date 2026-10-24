package com.briefwise.news.infrastructure.finnhub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.news.domain.model.NewsSourceUnavailableException;
import com.briefwise.shared.domain.model.Ticker;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Plain unit test against a local WireMock server: no Spring context, no network. */
class FinnhubNewsSourceTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final LocalDate FROM = LocalDate.parse("2026-10-19");
    private static final LocalDate TO = LocalDate.parse("2026-10-21");
    private static final String PATH = "/company-news";

    private static WireMockServer server;
    private FinnhubNewsSource source;

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        server.resetAll();
        source = sourceWithKey("test-key");
    }

    private FinnhubNewsSource sourceWithKey(String apiKey) {
        return new FinnhubNewsSource(new FinnhubProperties(server.baseUrl(), apiKey, Duration.ofSeconds(1),
                Duration.ofSeconds(1), 2, Duration.ofMillis(5))); // read timeout with margin for a cold WireMock
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = FinnhubNewsSourceTest.class.getResourceAsStream("/finnhub/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void maps_articles_sends_key_in_header_and_drops_malformed_ones() throws IOException {
        server.stubFor(get(urlPathEqualTo(PATH))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("from", equalTo("2026-10-19"))
                .withQueryParam("to", equalTo("2026-10-21"))
                .withHeader("X-Finnhub-Token", equalTo("test-key"))
                .willReturn(okJson(fixture("company-news-aapl.json"))));

        List<FetchedNews> news = source.fetch(AAPL, FROM, TO);

        assertThat(news).extracting(FetchedNews::headline).containsExactly(
                "Apple beats Q3 estimates as iPhone sales rise", "Apple supplier expands capacity in India");
        FetchedNews first = news.getFirst();
        assertThat(first.ticker()).isEqualTo(AAPL);
        assertThat(first.publishedAt()).isEqualTo(Instant.ofEpochSecond(1792836000));
        assertThat(first.source()).isEqualTo("Synthetic Wire");
        assertThat(first.url()).isEqualTo("https://news.example.com/apple-q3-beat");
        // The key never appears in the URL (it would end up in access logs).
        server.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("token", absent()));
    }

    @Test
    void empty_array_means_no_news() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("[]")));

        assertThat(source.fetch(AAPL, FROM, TO)).isEmpty();
    }

    @Test
    void rate_limit_is_retried() {
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("rate").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429)).willSetStateTo("open"));
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("rate").whenScenarioStateIs("open")
                .willReturn(okJson("[]")));

        assertThat(source.fetch(AAPL, FROM, TO)).isEmpty();
        server.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void server_error_after_all_retries_makes_the_ticker_unavailable() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> source.fetch(AAPL, FROM, TO))
                .isInstanceOf(NewsSourceUnavailableException.class)
                .hasMessageContaining("HTTP 503");
        server.verify(3, getRequestedFor(urlPathEqualTo(PATH))); // 1 attempt + 2 retries
    }

    @Test
    void timeout_is_retried_then_unavailable() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("[]").withFixedDelay(2000)));

        assertThatThrownBy(() -> source.fetch(AAPL, FROM, TO))
                .isInstanceOf(NewsSourceUnavailableException.class)
                .hasMessageContaining("I/O error");
        server.verify(3, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void malformed_body_is_not_retried() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{\"error\":\"unexpected\"}")));

        assertThatThrownBy(() -> source.fetch(AAPL, FROM, TO))
                .isInstanceOf(NewsSourceUnavailableException.class)
                .hasMessageContaining("unexpected response");
        server.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void rejected_key_is_a_configuration_error_and_not_retried() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> source.fetch(AAPL, FROM, TO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejected the API key");
        server.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void missing_key_fails_without_calling_finnhub() {
        FinnhubNewsSource withoutKey = sourceWithKey("");

        assertThatThrownBy(() -> withoutKey.fetch(AAPL, FROM, TO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINNHUB_API_KEY");
        server.verify(0, getRequestedFor(urlPathEqualTo(PATH)));
    }
}
