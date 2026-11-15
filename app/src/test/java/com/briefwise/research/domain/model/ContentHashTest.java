package com.briefwise.research.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentHashTest {

    @Test
    void same_sections_give_the_same_hash() {
        List<FilingSection> sections = List.of(new FilingSection("1A", "Risk Factors", "Text."));
        assertThat(ContentHash.of(sections)).isEqualTo(ContentHash.of(List.copyOf(sections))).hasSize(64);
    }

    @Test
    void any_change_in_text_title_or_order_changes_the_hash() {
        FilingSection a = new FilingSection("1", "Business", "We make phones.");
        FilingSection b = new FilingSection("1A", "Risk Factors", "Supply may fail.");
        String base = ContentHash.of(List.of(a, b));

        assertThat(ContentHash.of(List.of(b, a))).isNotEqualTo(base);
        assertThat(ContentHash.of(List.of(a, new FilingSection("1A", "Risk factors", "Supply may fail."))))
                .isNotEqualTo(base);
        assertThat(ContentHash.of(List.of(a, new FilingSection("1A", "Risk Factors", "Supply may fail!"))))
                .isNotEqualTo(base);
    }

    @Test
    void moving_text_across_a_field_boundary_changes_the_hash() {
        String joined = ContentHash.of(List.of(new FilingSection("7", "MD&A", "AB")));
        String split = ContentHash.of(List.of(new FilingSection("7", "MD&AA", "B")));
        assertThat(split).isNotEqualTo(joined);
    }
}
