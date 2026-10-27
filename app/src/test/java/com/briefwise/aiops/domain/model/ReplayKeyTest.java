package com.briefwise.aiops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReplayKeyTest {

    private static ReplayKey key(String input) {
        return ReplayKey.of("news-classification", "claude-haiku-5-5", "v1", input);
    }

    @Test
    void key_is_a_stable_sha256_hex_string() {
        assertThat(key("headline: Apple beats").hash()).matches("[0-9a-f]{64}")
                .isEqualTo(key("headline: Apple beats").hash());
    }

    @Test
    void line_endings_and_surrounding_or_repeated_whitespace_do_not_change_the_key() {
        ReplayKey lf = key("<news_item>\nheadline: Apple beats\n</news_item>");

        assertThat(key("<news_item>\r\nheadline: Apple beats\r\n</news_item>\r\n")).isEqualTo(lf);
        assertThat(key("  <news_item>\n\nheadline:   Apple\tbeats\n</news_item>  ")).isEqualTo(lf);
    }

    @Test
    void composed_and_decomposed_unicode_give_the_same_key() {
        assertThat(key("Nestlé raises guidance")).isEqualTo(key("Nestlé raises guidance"));
    }

    @Test
    void case_and_punctuation_are_kept_because_they_can_change_the_answer() {
        assertThat(key("Apple cuts guidance")).isNotEqualTo(key("apple cuts guidance"));
        assertThat(key("Apple cuts guidance")).isNotEqualTo(key("Apple cuts guidance?"));
    }

    @Test
    void every_key_part_changes_the_key() {
        ReplayKey base = ReplayKey.of("f", "m", "v1", "input");

        assertThat(ReplayKey.of("g", "m", "v1", "input")).isNotEqualTo(base);
        assertThat(ReplayKey.of("f", "n", "v1", "input")).isNotEqualTo(base);
        assertThat(ReplayKey.of("f", "m", "v2", "input")).isNotEqualTo(base);
        assertThat(ReplayKey.of("f", "m", "v1", "other")).isNotEqualTo(base);
    }

    @Test
    void parts_are_separated_so_shifting_text_between_them_changes_the_key() {
        assertThat(ReplayKey.of("ab", "m", "v1", "x")).isNotEqualTo(ReplayKey.of("a", "bm", "v1", "x"));
    }

    @Test
    void blank_parts_and_malformed_hashes_are_rejected() {
        assertThatThrownBy(() -> ReplayKey.of("f", " ", "v1", "x")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> ReplayKey.of("f", "m", "v1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayKey("ABC")).isInstanceOf(IllegalArgumentException.class);
    }
}
