package com.briefwise.aiops.domain.model;

/** A model is called that has no entry in {@code briefwise.aiops.pricing}: a configuration error. */
public class UnpricedModelException extends RuntimeException {

    public UnpricedModelException(String model) {
        super("No price configured for model '" + model + "' (briefwise.aiops.pricing)");
    }
}
