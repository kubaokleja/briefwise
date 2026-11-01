package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.NewsFeedItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Inbound port: the latest news in every visible status, optionally only for one portfolio's holdings. */
public interface NewsFeed {

    /** Largest page the feed returns. */
    int MAX_LIMIT = 200;

    /**
     * Newest first, at most {@code limit} items.
     *
     * @throws IllegalArgumentException if {@code limit} is not between 1 and {@link #MAX_LIMIT}
     * @throws com.briefwise.shared.domain.exception.ResourceNotFoundException if the portfolio does not exist
     */
    List<NewsFeedItem> latest(Optional<UUID> portfolioId, int limit);
}
