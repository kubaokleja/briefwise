package com.briefwise.aiops.domain.model;

import java.util.Map;
import java.util.Objects;

/** Prices per model name. Model names come from configuration only, never from code. */
public record PriceList(Map<String, ModelPrice> prices) {

    public PriceList {
        prices = Map.copyOf(Objects.requireNonNull(prices, "prices"));
    }

    /** @throws UnpricedModelException if the model has no price: a cost we cannot compute must not be logged as 0 */
    public ModelPrice priceOf(String model) {
        ModelPrice price = prices.get(model);
        if (price == null) {
            throw new UnpricedModelException(model);
        }
        return price;
    }
}
