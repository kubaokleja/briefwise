package com.briefwise.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CitationTest {

    @Test
    void keeps_its_parts() {
        Citation citation = new Citation(SourceType.FILING, "0000320193-25-000079", "Item 1A", "Supply chain risk");

        assertThat(citation.sourceId()).isEqualTo("0000320193-25-000079");
        assertThat(citation.locator()).isEqualTo("Item 1A");
    }

    @Test
    void a_citation_without_a_source_is_invalid() {
        assertThatThrownBy(() -> new Citation(SourceType.NEWS, " ", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Citation(null, "id", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optional_parts_default_to_empty() {
        Citation citation = new Citation(SourceType.MARKET_DATA, "AAPL", null, null);

        assertThat(citation.locator()).isEmpty();
        assertThat(citation.excerpt()).isEmpty();
    }
}
