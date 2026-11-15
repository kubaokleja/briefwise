package com.briefwise.research.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which parts of each form are kept (ADR 0025). Item ids: 10-K {@code 1, 1A, 7, 7A}; 10-Q part-qualified
 * {@code I-2, II-1A} (item numbers repeat across parts); 8-K {@code 2.02, 7.01, 8.01}, plus the EX-99.1 exhibit.
 */
public final class SectionPolicy {

    public static final String EX_99_1 = "EX-99.1";

    private static final Map<FilingType, List<String>> ITEMS = Map.of(
            FilingType.TEN_K, List.of("1", "1A", "7", "7A"),
            FilingType.TEN_Q, List.of("I-2", "II-1A"),
            FilingType.EIGHT_K, List.of("2.02", "7.01", "8.01"));

    private SectionPolicy() {
    }

    /** Items kept for {@code type}, in output order. */
    public static List<String> items(FilingType type) {
        return ITEMS.get(type);
    }

    /** Only 8-Ks carry the EX-99.1 press release we keep. */
    public static boolean keepsExhibit991(FilingType type) {
        return type == FilingType.EIGHT_K;
    }

    /** An 8-K is ingested only if it reports at least one kept item (results, Reg FD, other events). */
    public static boolean isRelevantEightK(Set<String> listedItems) {
        return listedItems.stream().anyMatch(ITEMS.get(FilingType.EIGHT_K)::contains);
    }
}
