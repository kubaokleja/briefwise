package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.NewsItem;

/** Outbound port: injection screening of an item's third-party text before it goes into a prompt (ADR 0013). */
public interface UntrustedContentScreen {

    /** True if the item must not be sent to an LLM. */
    boolean isSuspected(NewsItem item);
}
