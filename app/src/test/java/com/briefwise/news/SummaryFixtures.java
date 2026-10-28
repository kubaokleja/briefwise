package com.briefwise.news;

import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/** Classified and selected news items for summary tests. */
public final class SummaryFixtures {

    public static final Instant NOW = Instant.parse("2026-10-28T05:00:00Z");

    private SummaryFixtures() {
    }

    public static ClassifiedNews classified(String headline, Relevance relevance, Instant classifiedAt,
                                            String... tickers) {
        return new ClassifiedNews(UUID.randomUUID(), headline, "Summary of: " + headline,
                "https://example.com/" + Math.abs(headline.hashCode()), "Example Wire", NOW.minusSeconds(3600),
                classifiedAt, Arrays.stream(tickers).map(Ticker::new).collect(Collectors.toSet()),
                Sentiment.NEGATIVE, relevance, EventType.EARNINGS, "Rationale for: " + headline);
    }

    /**
     * Inserts a {@code CLASSIFIED} item with all tickers affected, published an hour ago and classified now.
     *
     * @return the item id
     */
    public static UUID insertClassified(org.springframework.jdbc.core.JdbcTemplate jdbc, String headline,
                                        Relevance relevance, String... tickers) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO news_items (id, headline, summary, url, url_hash, source, published_at, status, sentiment,
                                        relevance, event_type, rationale, prompt_version, classified_at)
                VALUES (?, ?, 'Synthetic summary.', ?, ?, 'Synthetic Wire', ?, 'CLASSIFIED', 'NEGATIVE', ?, 'EARNINGS',
                        'Synthetic rationale.', 'v1', ?)""",
                id, headline, "https://news.example.com/" + id, id.toString().replace("-", ""),
                java.sql.Timestamp.from(now.minusSeconds(3600)), relevance.name(), java.sql.Timestamp.from(now));
        for (String ticker : tickers) {
            jdbc.update("INSERT INTO news_item_tickers (news_item_id, ticker, affected) VALUES (?, ?, true)", id,
                    ticker);
        }
        return id;
    }

    public static RelevantNews relevant(String headline, Relevance relevance, Instant classifiedAt, String ticker,
                                        String weight) {
        return new RelevantNews(classified(headline, relevance, classifiedAt, ticker),
                java.util.Set.of(new Ticker(ticker)), new BigDecimal(weight));
    }
}
