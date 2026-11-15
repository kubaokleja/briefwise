package com.briefwise.research.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SectionPolicyTest {

    @Test
    void items_per_form_follow_adr_0025() {
        assertThat(SectionPolicy.items(FilingType.TEN_K)).containsExactly("1", "1A", "7", "7A");
        assertThat(SectionPolicy.items(FilingType.TEN_Q)).containsExactly("I-2", "II-1A");
        assertThat(SectionPolicy.items(FilingType.EIGHT_K)).containsExactly("2.02", "7.01", "8.01");
    }

    @Test
    void only_eight_ks_keep_the_press_release_exhibit() {
        assertThat(SectionPolicy.keepsExhibit991(FilingType.EIGHT_K)).isTrue();
        assertThat(SectionPolicy.keepsExhibit991(FilingType.TEN_K)).isFalse();
        assertThat(SectionPolicy.keepsExhibit991(FilingType.TEN_Q)).isFalse();
    }

    @Test
    void an_eight_k_is_relevant_if_it_reports_a_kept_item() {
        assertThat(SectionPolicy.isRelevantEightK(Set.of("2.02", "9.01"))).isTrue();
        assertThat(SectionPolicy.isRelevantEightK(Set.of("8.01"))).isTrue();
        assertThat(SectionPolicy.isRelevantEightK(Set.of("5.02", "9.01"))).isFalse();
        assertThat(SectionPolicy.isRelevantEightK(Set.of())).isFalse();
    }
}
