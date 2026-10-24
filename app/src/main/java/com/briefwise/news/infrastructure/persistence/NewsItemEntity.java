package com.briefwise.news.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Read back by the {@code news.raw} consumer through {@link NewsEntityMapper}. Tickers added to an existing item and
 * status transitions go through native statements (see {@link NewsItemJpaRepository}).
 */
@Entity
@Table(name = "news_items")
class NewsItemEntity {

    @Id
    private UUID id;

    @Column(name = "headline", nullable = false)
    private String headline;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "url_hash", nullable = false)
    private String urlHash;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @ElementCollection
    @CollectionTable(name = "news_item_tickers", joinColumns = @JoinColumn(name = "news_item_id"))
    @Column(name = "ticker", nullable = false)
    private Set<String> tickers = new HashSet<>();

    protected NewsItemEntity() {
    }

    NewsItemEntity(UUID id, String headline, String summary, String url, String urlHash, String source,
                   Instant publishedAt, String status, UUID duplicateOfId, Set<String> tickers) {
        this.id = id;
        this.headline = headline;
        this.summary = summary;
        this.url = url;
        this.urlHash = urlHash;
        this.source = source;
        this.publishedAt = publishedAt;
        this.status = status;
        this.duplicateOfId = duplicateOfId;
        this.tickers = new HashSet<>(tickers);
    }

    public UUID getId() {
        return id;
    }

    public String getHeadline() {
        return headline;
    }

    public String getSummary() {
        return summary;
    }

    public String getUrl() {
        return url;
    }

    public String getSource() {
        return source;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getStatus() {
        return status;
    }

    public UUID getDuplicateOfId() {
        return duplicateOfId;
    }

    public Set<String> getTickers() {
        return tickers;
    }
}
