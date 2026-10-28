package com.briefwise.news.domain.model;

import java.util.Objects;

/** The committed reference run the regression gate compares against (ADR 0018). */
public record EvalBaseline(String model, String promptVersion, double sentimentMacroF1) {

    public EvalBaseline {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(promptVersion, "promptVersion");
    }
}
