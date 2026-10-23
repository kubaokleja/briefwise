package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.shared.domain.model.Ticker;
import java.time.LocalDate;
import java.util.List;

/** Outbound port: an external company news provider (Finnhub). */
public interface NewsSource {

    /**
     * Articles about {@code ticker} published between the UTC dates (inclusive). Malformed articles are dropped.
     *
     * @throws com.briefwise.news.domain.model.NewsSourceUnavailableException if the source fails after retries
     * @throws IllegalStateException if the source is misconfigured (e.g. no or a rejected API key)
     */
    List<FetchedNews> fetch(Ticker ticker, LocalDate from, LocalDate to);
}
