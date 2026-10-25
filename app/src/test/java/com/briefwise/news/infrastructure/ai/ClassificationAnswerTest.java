package com.briefwise.news.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

class ClassificationAnswerTest {

    private static final NewsItem ITEM = new NewsItem(UUID.randomUUID(), "Apple and Microsoft headline", "",
            "https://example.com/1", Instant.parse("2026-10-24T06:00:00Z"), "Reuters",
            Set.of(new Ticker("AAPL"), new Ticker("MSFT")), NewsStatus.RECEIVED, Optional.empty());

    private static ClassificationAnswer answer(List<String> tickers) {
        return new ClassificationAnswer("Earnings beat.", Sentiment.POSITIVE, Relevance.HIGH, EventType.EARNINGS,
                tickers);
    }

    @Test
    void valid_answer_becomes_a_classification_with_the_prompt_version() {
        NewsClassification c = answer(List.of("aapl ")).toClassification(ITEM, "v1");

        assertThat(c.tickers()).containsExactly(new Ticker("AAPL"));
        assertThat(c.promptVersion()).isEqualTo("v1");
        assertThat(c.eventType()).isEqualTo(EventType.EARNINGS);
    }

    @Test
    void a_ticker_the_item_does_not_have_is_invalid_output() {
        assertThatThrownBy(() -> answer(List.of("AAPL", "TSLA")).toClassification(ITEM, "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TSLA");
    }

    @Test
    void no_affected_ticker_or_missing_label_is_invalid_output() {
        assertThatThrownBy(() -> answer(List.of()).toClassification(ITEM, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClassificationAnswer("r", null, Relevance.LOW, EventType.OTHER, List.of("AAPL"))
                .toClassification(ITEM, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClassificationAnswer(" ", Sentiment.NEUTRAL, Relevance.LOW, EventType.OTHER,
                List.of("AAPL")).toClassification(ITEM, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generated_schema_is_strict_lists_every_label_and_asks_for_the_rationale_first() {
        String schema = new BeanOutputConverter<>(ClassificationAnswer.class).getJsonSchema();

        // Anthropic structured outputs reject objects without additionalProperties: false
        assertThat(schema).contains("\"additionalProperties\" : false")
                .contains("\"ANALYST\"")
                .contains("\"M_AND_A\"");
        assertThat(schema.indexOf("\"rationale\"")).isLessThan(schema.indexOf("\"sentiment\""));
    }
}
