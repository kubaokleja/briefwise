package com.briefwise.compliance.domain.model;

/** The compliance rules for texts (DESIGN 11) that are checked in code (M1 slice 7, ADR 0017). */
public enum RuleId {
    /** No buy, sell or hold recommendations (rule 1). */
    NO_RECOMMENDATION,
    /** No price forecasts or performance promises (rule 2). */
    NO_FORECAST,
    /** Every factual passage cites at least one source (rule 3). */
    CITATION_REQUIRED,
    /** The fixed disclaimer is part of the text (rule 4). */
    DISCLAIMER_REQUIRED
}
