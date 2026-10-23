package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;

/** The news source could not deliver news for one ticker (after retries). Ingest goes on with the next ticker. */
public class NewsSourceUnavailableException extends RuntimeException {

    private final transient Ticker ticker;

    public NewsSourceUnavailableException(Ticker ticker, String reason, Throwable cause) {
        super("News for " + ticker + " unavailable: " + reason, cause);
        this.ticker = ticker;
    }

    public Ticker ticker() {
        return ticker;
    }
}
