package com.briefwise.research.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Cik, AccessionNo, FilingType and Screening: the small value objects of research. */
class IdentifiersTest {

    @Test
    void cik_is_padded_to_ten_digits_and_unpadded_for_archive_paths() {
        Cik cik = new Cik(" 320193 ");
        assertThat(cik.value()).isEqualTo("0000320193");
        assertThat(cik.unpadded()).isEqualTo("320193");
        assertThat(new Cik("0000320193")).isEqualTo(cik);
    }

    @Test
    void cik_rejects_non_digits_and_more_than_ten_digits() {
        assertThatThrownBy(() -> new Cik("CIK320193")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cik("12345678901")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cik("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cik(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accession_accepts_dashed_and_plain_form_and_stores_dashed() {
        AccessionNo plain = new AccessionNo("000032019325000079");
        assertThat(plain.value()).isEqualTo("0000320193-25-000079");
        assertThat(plain).isEqualTo(new AccessionNo("0000320193-25-000079"));
        assertThat(plain.withoutDashes()).isEqualTo("000032019325000079");
    }

    @Test
    void accession_rejects_other_shapes() {
        assertThatThrownBy(() -> new AccessionNo("0000320193-2-5000079")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessionNo("00003201932500007")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessionNo(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filing_type_maps_exact_forms_only() {
        assertThat(FilingType.fromForm("10-K")).contains(FilingType.TEN_K);
        assertThat(FilingType.fromForm(" 10-q ")).contains(FilingType.TEN_Q);
        assertThat(FilingType.fromForm("8-K")).contains(FilingType.EIGHT_K);
        assertThat(FilingType.fromForm("10-K/A")).isEmpty();
        assertThat(FilingType.fromForm("8-K/A")).isEmpty();
        assertThat(FilingType.fromForm("DEF 14A")).isEmpty();
        assertThat(FilingType.fromForm(null)).isEmpty();
    }

    @Test
    void screening_is_suspected_exactly_when_rules_matched() {
        assertThat(Screening.CLEAN.suspected()).isFalse();
        assertThat(new Screening(true, List.of("instruction-override")).matchedRules())
                .containsExactly("instruction-override");
        assertThatThrownBy(() -> new Screening(true, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Screening(false, List.of("x"))).isInstanceOf(IllegalArgumentException.class);
    }
}
