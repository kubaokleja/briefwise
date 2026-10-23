package com.briefwise.news.infrastructure.finnhub;

import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.news.domain.model.NewsSourceUnavailableException;
import com.briefwise.news.domain.port.NewsSource;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Outbound adapter for Finnhub {@code GET /company-news} (free tier: North American companies, one year of
 * history). The key goes in the {@code X-Finnhub-Token} header, never in the URL, so it cannot leak into logs.
 * Transient failures (429, 5xx, timeouts) are retried with Spring's core {@link RetryTemplate} (ADR 0005);
 * a rejected key (401/403) is a configuration error and is not retried.
 */
@Component
class FinnhubNewsSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(FinnhubNewsSource.class);

    private final RestClient client;
    private final RetryTemplate retry;
    private final boolean keyConfigured;

    FinnhubNewsSource(FinnhubProperties properties) {
        // Socket timeouts (HttpURLConnection): the read timeout is per read, so a slow but steady body still arrives.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Finnhub-Token", properties.apiKey())
                .build();
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .includes(TransientFailure.class)
                .maxRetries(properties.maxRetries())
                .delay(properties.retryDelay())
                .multiplier(2)
                .build());
        this.keyConfigured = !properties.apiKey().isBlank();
    }

    @Override
    public List<FetchedNews> fetch(Ticker ticker, LocalDate from, LocalDate to) {
        if (!keyConfigured) {
            throw new IllegalStateException("No Finnhub API key: set FINNHUB_API_KEY (briefwise.news.finnhub.api-key)");
        }
        FinnhubArticle[] articles;
        try {
            articles = retry.invoke(() -> call(ticker, from, to));
        } catch (TransientFailure e) {
            throw new NewsSourceUnavailableException(ticker, e.getMessage(), e);
        } catch (RestClientException e) {
            // Other 4xx, or a body that is not the expected JSON array: retrying would not help.
            throw new NewsSourceUnavailableException(ticker, "unexpected response: " + e.getMessage(), e);
        }
        return articles == null ? List.of() : toFetched(ticker, articles);
    }

    private FinnhubArticle[] call(Ticker ticker, LocalDate from, LocalDate to) {
        try {
            return client.get()
                    .uri(uri -> uri.path("/company-news")
                            .queryParam("symbol", ticker.symbol())
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build())
                    .retrieve()
                    .onStatus(status -> status.value() == 401 || status.value() == 403, (request, response) -> {
                        throw new IllegalStateException(
                                "Finnhub rejected the API key (HTTP " + response.getStatusCode().value() + ")");
                    })
                    .onStatus(status -> status.value() == 429 || status.is5xxServerError(), (request, response) -> {
                        throw new TransientFailure("HTTP " + response.getStatusCode().value(), null);
                    })
                    .body(FinnhubArticle[].class);
        } catch (ResourceAccessException e) {
            throw new TransientFailure("I/O error: " + e.getMessage(), e);
        }
    }

    private static List<FetchedNews> toFetched(Ticker ticker, FinnhubArticle[] articles) {
        List<FetchedNews> fetched = new ArrayList<>(articles.length);
        for (FinnhubArticle article : articles) {
            try {
                if (article.datetime() <= 0) {
                    throw new IllegalArgumentException("missing datetime");
                }
                fetched.add(new FetchedNews(ticker, article.headline(), article.summary(), article.url(),
                        Instant.ofEpochSecond(article.datetime()), article.source()));
            } catch (IllegalArgumentException e) {
                log.warn("Dropping Finnhub article {} for {}: {}", article.id(), ticker, e.getMessage());
            }
        }
        return fetched;
    }

    /** Worth another attempt: rate limit, server error, timeout or connection failure. */
    private static final class TransientFailure extends RuntimeException {

        TransientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
