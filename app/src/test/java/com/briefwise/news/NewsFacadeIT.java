package com.briefwise.news;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.NewsFacade.IngestSummaryView;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End to end over the seeded portfolios (9 companies + SPY, AGG) with Finnhub stubbed by WireMock: held tickers
 * from portfolio, ETF kind from market, dedup in Postgres.
 */
class NewsFacadeIT extends AbstractIntegrationTest {

    private static final String PATH = "/company-news";

    @Autowired
    NewsFacade facade;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM news_items");
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).atPriority(10).willReturn(okJson("[]")));
    }

    private String article(long id, String headline, String url, Duration age) {
        return """
                {"category":"company","datetime":%d,"headline":"%s","id":%d,"image":"","related":"",
                 "source":"Synthetic Wire","summary":"Synthetic test article.","url":"%s"}"""
                .formatted(now.minus(age).getEpochSecond(), headline, id, url);
    }

    private void stub(String symbol, String... articles) {
        WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("symbol", equalTo(symbol)).atPriority(1)
                .willReturn(okJson("[" + String.join(",", articles) + "]")));
    }

    @Test
    void ingests_held_companies_merges_shared_urls_and_skips_near_duplicates() {
        String shared = article(1, "Apple and Microsoft announce AI partnership", "https://news.example.com/shared",
                Duration.ofHours(5));
        stub("AAPL",
                article(2, "Apple beats Q3 estimates as iPhone sales rise, shares up 3%",
                        "https://news.example.com/apple-2", Duration.ofHours(1)),
                article(3, "Apple beats Q3 estimates as iPhone sales rise", "https://news.example.com/apple-1",
                        Duration.ofHours(3)),
                shared);
        stub("MSFT", shared);
        WIREMOCK.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("symbol", equalTo("NVDA")).atPriority(1)
                .willReturn(aResponse().withStatus(503)));

        IngestSummaryView summary = facade.ingest();

        assertThat(summary.skipped()).extracting(Ticker::symbol).containsExactly("AGG", "SPY");
        assertThat(summary.failed()).extracting(Ticker::symbol).containsExactly("NVDA");
        assertThat(summary.queried()).extracting(Ticker::symbol)
                .containsExactly("AAPL", "AMZN", "JNJ", "JPM", "KO", "MSFT", "PG", "XOM");
        assertThat(summary.fetched()).isEqualTo(4);
        assertThat(summary.inserted()).isEqualTo(2);       // shared + the older Apple headline
        assertThat(summary.nearDuplicates()).isEqualTo(1);  // the newer Apple headline
        assertThat(summary.alreadyKnown()).isEqualTo(1);    // shared URL again, for MSFT

        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("symbol", equalTo("SPY")));
        assertThat(jdbc.queryForList("""
                SELECT t.ticker FROM news_item_tickers t JOIN news_items n ON n.id = t.news_item_id
                WHERE n.url = 'https://news.example.com/shared' ORDER BY t.ticker""", String.class))
                .containsExactly("AAPL", "MSFT");
        Map<String, Object> skipped = jdbc.queryForMap("""
                SELECT d.status, o.url AS original_url FROM news_items d JOIN news_items o ON o.id = d.duplicate_of_id
                WHERE d.url = 'https://news.example.com/apple-2'""");
        assertThat(skipped).containsEntry("status", "SKIPPED")
                .containsEntry("original_url", "https://news.example.com/apple-1");
    }

    @Test
    void a_second_run_stores_nothing_new() {
        stub("AAPL", article(1, "Apple opens new campus", "https://news.example.com/campus", Duration.ofHours(2)));

        facade.ingest();
        IngestSummaryView second = facade.ingest();

        assertThat(second.inserted()).isZero();
        assertThat(second.alreadyKnown()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM news_items", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT status FROM news_items", String.class)).isEqualTo(List.of("RECEIVED"));
    }
}
