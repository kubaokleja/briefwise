package com.briefwise.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void adds_and_subtracts_in_the_same_currency() {
        assertThat(Money.of("10.50", "CHF").plus(Money.of("2.25", "CHF"))).isEqualTo(Money.of("12.75", "CHF"));
        assertThat(Money.of("10.50", "CHF").minus(Money.of("0.50", "CHF"))).isEqualTo(Money.of("10.00", "CHF"));
    }

    @Test
    void refuses_to_mix_currencies() {
        assertThatThrownBy(() -> Money.of("1", "CHF").plus(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHF")
                .hasMessageContaining("USD");
    }

    @Test
    void rejects_missing_parts() {
        assertThatThrownBy(() -> new Money(null, java.util.Currency.getInstance("CHF")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
