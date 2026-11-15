package com.briefwise.research.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FilingSelectionPolicyTest {

    private static final Cik CIK = new Cik("320193");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final FilingSelectionPolicy POLICY = new FilingSelectionPolicy(365);

    private static int sequence;

    private static FilingRef ref(FilingType type, String filedAt, String... items) {
        sequence++;
        return new FilingRef(new AccessionNo(String.format("0000320193-26-%06d", sequence)), CIK, type,
                LocalDate.parse(filedAt), Optional.empty(), "doc.htm", Set.of(items));
    }

    @Test
    void latest_ten_k_the_ten_qs_after_it_and_relevant_recent_eight_ks_oldest_first() {
        FilingRef oldTenK = ref(FilingType.TEN_K, "2024-11-01");
        FilingRef tenK = ref(FilingType.TEN_K, "2025-10-31");
        FilingRef qBeforeTenK = ref(FilingType.TEN_Q, "2025-08-01");
        FilingRef q1 = ref(FilingType.TEN_Q, "2026-01-30");
        FilingRef q2 = ref(FilingType.TEN_Q, "2026-05-01");
        FilingRef earnings = ref(FilingType.EIGHT_K, "2026-07-30", "2.02", "9.01");
        FilingRef officerChange = ref(FilingType.EIGHT_K, "2026-03-01", "5.02");
        FilingRef oldEarnings = ref(FilingType.EIGHT_K, "2025-10-30", "2.02");

        List<FilingRef> selected = POLICY.select(
                List.of(q2, earnings, oldTenK, officerChange, tenK, qBeforeTenK, q1, oldEarnings), TODAY);

        assertThat(selected).containsExactly(tenK, q1, q2, earnings);
    }

    @Test
    void eight_k_on_the_cutoff_day_is_kept_and_the_day_before_is_not() {
        FilingRef onCutoff = ref(FilingType.EIGHT_K, "2025-11-14", "8.01");
        FilingRef dayBefore = ref(FilingType.EIGHT_K, "2025-11-13", "8.01");
        assertThat(POLICY.select(List.of(onCutoff, dayBefore), TODAY)).containsExactly(onCutoff);
    }

    @Test
    void without_a_ten_k_the_ten_qs_within_the_lookback_are_kept() {
        FilingRef recent = ref(FilingType.TEN_Q, "2026-05-01");
        FilingRef old = ref(FilingType.TEN_Q, "2025-05-01");
        assertThat(POLICY.select(List.of(recent, old), TODAY)).containsExactly(recent);
    }

    @Test
    void a_listing_twice_is_selected_once() {
        FilingRef tenK = ref(FilingType.TEN_K, "2025-10-31");
        assertThat(POLICY.select(List.of(tenK, tenK), TODAY)).containsExactly(tenK);
    }

    @Test
    void empty_listing_selects_nothing_and_lookback_must_be_positive() {
        assertThat(POLICY.select(List.of(), TODAY)).isEmpty();
        assertThatThrownBy(() -> new FilingSelectionPolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
