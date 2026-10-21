package com.briefwise.aiops.domain.model;

import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** List price of one model in USD per million tokens. Input and output tokens are priced separately. */
public record ModelPrice(BigDecimal inputUsdPerMTok, BigDecimal outputUsdPerMTok) {

    /** Scale of stored costs (matches {@code NUMERIC(14, 8)}): one Haiku input token is 0.0000001 USD. */
    public static final int COST_SCALE = 8;

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    public ModelPrice {
        Objects.requireNonNull(inputUsdPerMTok, "inputUsdPerMTok");
        Objects.requireNonNull(outputUsdPerMTok, "outputUsdPerMTok");
        if (inputUsdPerMTok.signum() < 0 || outputUsdPerMTok.signum() < 0) {
            throw new IllegalArgumentException("Model prices must not be negative");
        }
    }

    public BigDecimal costOf(TokenCount tokens) {
        BigDecimal input = inputUsdPerMTok.multiply(BigDecimal.valueOf(tokens.promptTokens()));
        BigDecimal output = outputUsdPerMTok.multiply(BigDecimal.valueOf(tokens.completionTokens()));
        return input.add(output).divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }
}
