package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Objects;

/** One article as returned by a news source for {@code ticker}, before deduplication. */
public record FetchedNews(Ticker ticker, String headline, String summary, String url, Instant publishedAt,
                          String source) {

    public FetchedNews {
        Objects.requireNonNull(ticker, "ticker");
        headline = requireText(headline, "headline");
        summary = summary == null ? "" : summary.strip();
        url = requireText(url, "url");
        Objects.requireNonNull(publishedAt, "publishedAt");
        source = requireText(source, "source");
        UrlHash.of(url); // rejects URLs that cannot be hashed (not absolute http/https)
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
