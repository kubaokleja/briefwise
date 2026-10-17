package com.briefwise.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TickerTest {

    @Test
    void normalises_case_and_whitespace() {
        assertThat(new Ticker("  aapl ").symbol()).isEqualTo("AAPL");
        assertThat(new Ticker("aapl")).isEqualTo(new Ticker("AAPL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BRK.B", "BF-B", "SPY", "A"})
    void accepts_real_symbols(String symbol) {
        assertThat(new Ticker(symbol).symbol()).isEqualTo(symbol);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "123", "AAPL!", "TOOLONGSYMBOL1", "A B"})
    void rejects_invalid_symbols(String symbol) {
        assertThatThrownBy(() -> new Ticker(symbol)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new Ticker(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
