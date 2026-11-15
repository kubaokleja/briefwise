package com.briefwise.research.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the filings to ingest from a company's listing (ADR 0025): the latest 10-K, the 10-Qs filed after it, and
 * the 8-Ks filed within {@code lookbackDays} that report a kept item. Without any 10-K, 10-Qs within the lookback.
 * Result oldest first.
 */
public record FilingSelectionPolicy(int lookbackDays) {

    public FilingSelectionPolicy {
        if (lookbackDays < 1) {
            throw new IllegalArgumentException("lookbackDays must be positive, was " + lookbackDays);
        }
    }

    public List<FilingRef> select(List<FilingRef> listed, LocalDate today) {
        LocalDate cutoff = today.minusDays(lookbackDays);
        Map<AccessionNo, FilingRef> unique = new LinkedHashMap<>();
        listed.forEach(ref -> unique.putIfAbsent(ref.accessionNo(), ref));

        Optional<FilingRef> latestTenK = unique.values().stream()
                .filter(ref -> ref.type() == FilingType.TEN_K)
                .max(Comparator.comparing(FilingRef::filedAt));
        LocalDate tenQAfter = latestTenK.map(FilingRef::filedAt).orElse(cutoff.minusDays(1));

        List<FilingRef> selected = new ArrayList<>();
        latestTenK.ifPresent(selected::add);
        for (FilingRef ref : unique.values()) {
            boolean keep = switch (ref.type()) {
                case TEN_K -> false;
                case TEN_Q -> ref.filedAt().isAfter(tenQAfter);
                case EIGHT_K -> !ref.filedAt().isBefore(cutoff) && SectionPolicy.isRelevantEightK(ref.items());
            };
            if (keep) {
                selected.add(ref);
            }
        }
        selected.sort(Comparator.comparing(FilingRef::filedAt).thenComparing(ref -> ref.accessionNo().value()));
        return List.copyOf(selected);
    }
}
