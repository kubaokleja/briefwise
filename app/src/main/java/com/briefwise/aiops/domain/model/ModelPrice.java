package com.briefwise.aiops.domain.model;

import com.briefwise.shared.domain.model.PricingTier;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * List price of one model in USD per million tokens. Input, output, cache-write and cache-read tokens are priced
 * separately (prompt caching, ADR 0012). A Message Batch request costs half of each ({@link PricingTier#BATCH}).
 */
public record ModelPrice(BigDecimal inputUsdPerMTok, BigDecimal outputUsdPerMTok, BigDecimal cacheWriteUsdPerMTok,
                         BigDecimal cacheReadUsdPerMTok) {

    /** Scale of stored costs (matches {@code NUMERIC(14, 8)}): one Haiku input token is 0.0000001 USD. */
    public static final int COST_SCALE = 8;

    /** Anthropic's standard multipliers on the input price: 5-minute cache write 1.25x, cache read 0.1x. */
    static final BigDecimal STANDARD_CACHE_WRITE_FACTOR = new BigDecimal("1.25");
    static final BigDecimal STANDARD_CACHE_READ_FACTOR = new BigDecimal("0.1");

    /** Anthropic's Message Batches discount: 50% of every token price, stacking with the cache multipliers. */
    static final BigDecimal BATCH_FACTOR = new BigDecimal("0.5");

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    public ModelPrice {
        Objects.requireNonNull(inputUsdPerMTok, "inputUsdPerMTok");
        Objects.requireNonNull(outputUsdPerMTok, "outputUsdPerMTok");
        Objects.requireNonNull(cacheWriteUsdPerMTok, "cacheWriteUsdPerMTok");
        Objects.requireNonNull(cacheReadUsdPerMTok, "cacheReadUsdPerMTok");
        if (inputUsdPerMTok.signum() < 0 || outputUsdPerMTok.signum() < 0 || cacheWriteUsdPerMTok.signum() < 0
                || cacheReadUsdPerMTok.signum() < 0) {
            throw new IllegalArgumentException("Model prices must not be negative");
        }
    }

    /** Cache prices derived from the input price with the standard multipliers. */
    public ModelPrice(BigDecimal inputUsdPerMTok, BigDecimal outputUsdPerMTok) {
        this(inputUsdPerMTok, outputUsdPerMTok, standardCacheWrite(inputUsdPerMTok), standardCacheRead(inputUsdPerMTok));
    }

    public static BigDecimal standardCacheWrite(BigDecimal inputUsdPerMTok) {
        return Objects.requireNonNull(inputUsdPerMTok, "inputUsdPerMTok").multiply(STANDARD_CACHE_WRITE_FACTOR);
    }

    public static BigDecimal standardCacheRead(BigDecimal inputUsdPerMTok) {
        return Objects.requireNonNull(inputUsdPerMTok, "inputUsdPerMTok").multiply(STANDARD_CACHE_READ_FACTOR);
    }

    public BigDecimal costOf(TokenCount tokens) {
        return costOf(tokens, PricingTier.STANDARD);
    }

    public BigDecimal costOf(TokenCount tokens, PricingTier tier) {
        BigDecimal input = inputUsdPerMTok.multiply(BigDecimal.valueOf(tokens.promptTokens()));
        BigDecimal output = outputUsdPerMTok.multiply(BigDecimal.valueOf(tokens.completionTokens()));
        BigDecimal cacheWrite = cacheWriteUsdPerMTok.multiply(BigDecimal.valueOf(tokens.cacheWriteTokens()));
        BigDecimal cacheRead = cacheReadUsdPerMTok.multiply(BigDecimal.valueOf(tokens.cacheReadTokens()));
        BigDecimal listPrice = input.add(output).add(cacheWrite).add(cacheRead);
        BigDecimal billed = tier == PricingTier.BATCH ? listPrice.multiply(BATCH_FACTOR) : listPrice;
        return billed.divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }
}
