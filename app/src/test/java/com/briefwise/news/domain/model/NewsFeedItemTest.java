package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsFeedItemTest {

    private static final NewsFeedItem.Labels LABELS = new NewsFeedItem.Labels(Sentiment.NEGATIVE, Relevance.HIGH,
            EventType.EARNINGS, "Revenue below estimates.");

    private static NewsFeedItem item(NewsStatus status, Optional<NewsFeedItem.Labels> labels,
                                     Optional<UnclassifiedReason> reason) {
        return new NewsFeedItem(UUID.randomUUID(), "Apple misses estimates", "https://example.com/a", "Example Wire",
                Instant.parse("2026-10-28T05:00:00Z"), status, Set.of(new Ticker("AAPL")), labels, reason);
    }

    @Test
    void each_visible_status_carries_exactly_its_own_details() {
        assertThat(item(NewsStatus.RECEIVED, Optional.empty(), Optional.empty()).labels()).isEmpty();
        assertThat(item(NewsStatus.CLASSIFIED, Optional.of(LABELS), Optional.empty()).labels()).contains(LABELS);
        assertThat(item(NewsStatus.UNCLASSIFIED, Optional.empty(), Optional.of(UnclassifiedReason.INVALID_OUTPUT))
                .unclassifiedReason()).contains(UnclassifiedReason.INVALID_OUTPUT);
    }

    @Test
    void skipped_items_are_not_part_of_the_feed() {
        assertThatThrownBy(() -> item(NewsStatus.SKIPPED, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labels_only_with_classified_and_a_reason_only_with_unclassified() {
        assertThatThrownBy(() -> item(NewsStatus.CLASSIFIED, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(NewsStatus.RECEIVED, Optional.of(LABELS), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(NewsStatus.UNCLASSIFIED, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(NewsStatus.RECEIVED, Optional.empty(),
                Optional.of(UnclassifiedReason.PROVIDER_ERROR))).isInstanceOf(IllegalArgumentException.class);
    }
}
