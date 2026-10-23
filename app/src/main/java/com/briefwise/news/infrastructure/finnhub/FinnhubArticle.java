package com.briefwise.news.infrastructure.finnhub;

/**
 * One element of the {@code GET /company-news} response. {@code datetime} is Unix seconds; {@code related} is the
 * queried symbol. Fields we do not use ({@code category}, {@code image}) are ignored.
 */
record FinnhubArticle(long id, long datetime, String headline, String summary, String url, String source,
                      String related) {
}
