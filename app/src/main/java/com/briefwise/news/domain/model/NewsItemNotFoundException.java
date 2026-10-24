package com.briefwise.news.domain.model;

import java.util.UUID;

/** No news item with this id. For a consumed message this is a poison message: retrying cannot help. */
public class NewsItemNotFoundException extends RuntimeException {

    public NewsItemNotFoundException(UUID newsId) {
        super("No news item " + newsId);
    }
}
