package com.briefwise.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenCountTest {

    @Test
    void total_is_prompt_plus_completion() {
        assertThat(new TokenCount(1200, 300).total()).isEqualTo(1500);
    }

    @Test
    void total_includes_cached_prefix_tokens() {
        assertThat(new TokenCount(100, 50, 800, 0).total()).isEqualTo(950);
        assertThat(new TokenCount(100, 50, 0, 800).total()).isEqualTo(950);
    }

    @Test
    void negative_counts_are_invalid() {
        assertThatThrownBy(() -> new TokenCount(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenCount(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenCount(0, 0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenCount(0, 0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
