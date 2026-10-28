package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.GoldenCase;
import java.util.List;

/** Outbound port: the hand-labelled golden set of the classification eval (DESIGN 10, ADR 0018). */
public interface GoldenSet {

    /** @throws IllegalStateException if the golden set is missing (not decrypted) or malformed */
    List<GoldenCase> load();
}
