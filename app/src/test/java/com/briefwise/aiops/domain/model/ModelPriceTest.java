package com.briefwise.aiops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelPriceTest {

    private final ModelPrice haiku = new ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.50"));

    @Test
    void cost_prices_input_and_output_tokens_separately_per_million() {
        // 1,200 x 0.10 / 1M + 200 x 0.50 / 1M = 0.00012 + 0.0001
        assertThat(haiku.costOf(new TokenCount(1_200, 200))).isEqualByComparingTo("0.00022");
    }

    @Test
    void cost_keeps_eight_decimals_so_a_single_token_is_not_rounded_away() {
        assertThat(haiku.costOf(new TokenCount(1, 0))).isEqualTo(new BigDecimal("0.00000010"));
    }

    @Test
    void cached_prefix_tokens_use_the_cache_prices() {
        ModelPrice explicit = new ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.50"),
                new BigDecimal("0.125"), new BigDecimal("0.01"));

        // 100 x 0.10 + 50 x 0.50 + 800 x 0.125 + 0 = 10 + 25 + 100 -> 135 / 1M
        assertThat(explicit.costOf(new TokenCount(100, 50, 800, 0))).isEqualByComparingTo("0.000135");
        // 100 x 0.10 + 50 x 0.50 + 800 x 0.01 = 10 + 25 + 8 -> 43 / 1M
        assertThat(explicit.costOf(new TokenCount(100, 50, 0, 800))).isEqualByComparingTo("0.000043");
    }

    @Test
    void without_explicit_cache_prices_the_standard_multipliers_apply() {
        assertThat(haiku.cacheWriteUsdPerMTok()).isEqualByComparingTo("0.125");
        assertThat(haiku.cacheReadUsdPerMTok()).isEqualByComparingTo("0.01");
    }

    @Test
    void zero_tokens_cost_nothing() {
        assertThat(haiku.costOf(new TokenCount(0, 0))).isEqualByComparingTo("0");
    }

    @Test
    void negative_price_is_rejected() {
        assertThatThrownBy(() -> new ModelPrice(new BigDecimal("-1"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void price_list_fails_for_a_model_without_price_instead_of_assuming_zero() {
        PriceList prices = new PriceList(Map.of("claude-haiku-5-5", haiku));

        assertThat(prices.priceOf("claude-haiku-5-5")).isEqualTo(haiku);
        assertThatThrownBy(() -> prices.priceOf("unknown-model"))
                .isInstanceOf(UnpricedModelException.class)
                .hasMessageContaining("unknown-model");
    }
}
