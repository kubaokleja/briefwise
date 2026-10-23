package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlHashTest {

    @Test
    void hash_is_lower_case_sha256_hex() {
        assertThat(UrlHash.of("https://example.com/a").value()).matches("[0-9a-f]{64}");
    }

    @Test
    void scheme_and_host_case_and_fragment_do_not_matter() {
        assertThat(UrlHash.of("HTTPS://Example.COM/news/1?id=7#top"))
                .isEqualTo(UrlHash.of("https://example.com/news/1?id=7"));
    }

    @Test
    void path_and_query_do_matter() {
        assertThat(UrlHash.of("https://example.com/news?id=1")).isNotEqualTo(UrlHash.of("https://example.com/news?id=2"));
        assertThat(UrlHash.of("https://example.com/News")).isNotEqualTo(UrlHash.of("https://example.com/news"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not a url", "/relative/path", "ftp://example.com/x", "mailto:a@example.com"})
    void rejects_urls_that_are_not_absolute_http(String url) {
        assertThatThrownBy(() -> UrlHash.of(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_values_that_are_not_a_digest() {
        assertThatThrownBy(() -> new UrlHash("abc")).isInstanceOf(IllegalArgumentException.class);
    }
}
