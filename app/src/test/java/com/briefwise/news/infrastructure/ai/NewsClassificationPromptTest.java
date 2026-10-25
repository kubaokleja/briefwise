package com.briefwise.news.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsClassificationPromptTest {

    private final NewsClassificationPrompt prompt = NewsClassificationPrompt.fromClasspath(40);

    private static NewsItem item(String headline, String summary, String... tickers) {
        Set<Ticker> set = Set.of(tickers).stream().map(Ticker::new).collect(java.util.stream.Collectors.toSet());
        return new NewsItem(UUID.randomUUID(), headline, summary, "https://example.com/1",
                Instant.parse("2026-10-24T06:00:00Z"), "Reuters", set, NewsStatus.RECEIVED, Optional.empty());
    }

    @Test
    void prompt_files_match_the_recorded_hash_so_a_change_needs_a_version_bump() {
        assertThat(prompt.contentSha256())
                .as("system.md or user.md changed: bump version in prompt.properties and set sha256 to this value")
                .isEqualTo(prompt.recordedSha256());
        assertThat(prompt.version()).isNotBlank();
    }

    @Test
    void stable_prefix_is_long_enough_to_be_cached() {
        // Haiku 5.5 caches prefixes from 512 tokens (ADR 0012). About 4 characters per token is a low estimate for
        // English on the current tokenizer, so 2,048 characters is a safe lower bound.
        assertThat(prompt.system().length()).isGreaterThan(512 * 4);
    }

    @Test
    void data_block_has_the_sorted_tickers_and_the_item_fields() {
        String rendered = prompt.render(item("Apple beats estimates", "Revenue up 8%.", "MSFT", "AAPL"));

        assertThat(rendered).isEqualTo("""
                <news_item>
                tickers: AAPL, MSFT
                source: Reuters
                headline: Apple beats estimates
                summary: Revenue up 8%.
                </news_item>
                """);
    }

    @Test
    void placeholders_inside_item_text_stay_literal() {
        String rendered = prompt.render(item("Odd {summary} headline $1", "real summary", "AAPL"));

        assertThat(rendered).contains("headline: Odd {summary} headline $1").contains("summary: real summary");
    }

    @Test
    void long_summaries_are_cut_to_bound_the_cost() {
        String rendered = prompt.render(item("Headline", "x".repeat(100), "AAPL"));

        assertThat(rendered).contains("summary: " + "x".repeat(40) + "...\n");
    }
}
