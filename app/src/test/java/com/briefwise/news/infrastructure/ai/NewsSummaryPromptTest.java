package com.briefwise.news.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.news.domain.model.SummaryViolation;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsSummaryPromptTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final NewsSummaryPrompt prompt = NewsSummaryPrompt.fromClasspath(40);

    private static RelevantNews item(String headline, String summary) {
        ClassifiedNews news = new ClassifiedNews(ID, headline, summary, "https://example.com/1", "Reuters",
                Instant.parse("2026-10-27T22:00:00Z"), Instant.parse("2026-10-28T04:31:00Z"),
                Set.of(new Ticker("AAPL"), new Ticker("MSFT"), new Ticker("GOOGL")), Sentiment.NEGATIVE,
                Relevance.HIGH, EventType.EARNINGS, "Revenue missed estimates.");
        return new RelevantNews(news, Set.of(new Ticker("MSFT"), new Ticker("AAPL")), new BigDecimal("0.2"));
    }

    private static SummaryRequest request(RelevantNews item, List<SummaryViolation> corrections) {
        return new SummaryRequest(UUID.randomUUID(), List.of(item), corrections);
    }

    @Test
    void prompt_files_match_the_recorded_hash_so_a_change_needs_a_version_bump() {
        assertThat(prompt.contentSha256())
                .as("system.md or user.md changed: bump version in prompt.properties and set sha256 to this value")
                .isEqualTo(prompt.recordedSha256());
        assertThat(prompt.version()).isNotBlank();
    }

    @Test
    void data_block_has_the_held_tickers_labels_and_screened_fields_only() {
        String rendered = prompt.render(request(item("Apple misses estimates", "Revenue fell 3%."), List.of()));

        assertThat(rendered).isEqualTo("""
                <data>
                <news_item id="00000000-0000-0000-0000-000000000001">
                tickers: AAPL, MSFT
                published: 2026-10-27
                relevance: HIGH
                sentiment: NEGATIVE
                event: EARNINGS
                headline: Apple misses estimates
                summary: Revenue fell 3%.
                </news_item>
                </data>
                """);
        assertThat(rendered).doesNotContain("Revenue missed estimates.").doesNotContain("GOOGL")
                .doesNotContain("0.2");
    }

    @Test
    void corrections_follow_the_data_on_a_re_ask() {
        String rendered = prompt.render(request(item("Apple misses estimates", ""), List.of(
                new SummaryViolation("NO_FORECAST", "will rise"), new SummaryViolation("CITATION_REQUIRED", ""))));

        assertThat(rendered).endsWith("""
                </data>
                <corrections>
                - NO_FORECAST: "will rise"
                - CITATION_REQUIRED
                </corrections>
                """);
    }

    @Test
    void placeholders_inside_item_text_stay_literal() {
        String rendered = prompt.render(request(item("Odd {corrections} headline $1", "{items}"), List.of()));

        assertThat(rendered).contains("headline: Odd {corrections} headline $1").contains("summary: {items}")
                .doesNotContain("<corrections>");
    }

    @Test
    void long_summaries_are_cut_to_bound_the_cost() {
        String rendered = prompt.render(request(item("Headline", "x".repeat(100)), List.of()));

        assertThat(rendered).contains("summary: " + "x".repeat(40) + "...\n");
    }
}
