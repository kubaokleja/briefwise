package com.briefwise.news.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoldenSetFingerprintTest {

    private static final GoldenCase FIRST = new GoldenCase(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            true, Set.of(new Ticker("MSFT"), new Ticker("AAPL")), "Wire", "Apple beats", "Summary",
            new EvalLabels(Sentiment.POSITIVE, Relevance.HIGH, EventType.EARNINGS));
    private static final GoldenCase SECOND = new GoldenCase(UUID.fromString("00000000-0000-0000-0000-000000000002"),
            false, Set.of(new Ticker("NESN")), "Wire", "Nestle update", "",
            new EvalLabels(Sentiment.NEUTRAL, Relevance.LOW, EventType.OTHER));

    @Test
    void counts_the_cases_and_hashes_to_lower_case_hex() {
        GoldenSetFingerprint fingerprint = GoldenSetFingerprint.of(List.of(FIRST, SECOND));

        assertThat(fingerprint.cases()).isEqualTo(2);
        assertThat(fingerprint.sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void row_order_does_not_change_the_hash() {
        assertThat(GoldenSetFingerprint.of(List.of(SECOND, FIRST)))
                .isEqualTo(GoldenSetFingerprint.of(List.of(FIRST, SECOND)));
    }

    @Test
    void the_canonical_form_is_stable() {
        // Pinned on purpose, computed independently from the documented canonical form: a change here makes every
        // committed baseline.json not comparable (ADR 0022).
        assertThat(hash(FIRST, SECOND)).isEqualTo("5599bb361feabb1bf79a6ed42b851acb6d872435300535d3412013268069a638");
    }

    @Test
    void a_relabel_changes_the_hash() {
        GoldenCase relabelled = new GoldenCase(SECOND.id(), SECOND.random(), SECOND.tickers(), SECOND.source(),
                SECOND.headline(), SECOND.summary(), new EvalLabels(Sentiment.NEGATIVE, Relevance.LOW, EventType.OTHER));

        assertThat(hash(FIRST, relabelled)).isNotEqualTo(hash(FIRST, SECOND));
    }

    @Test
    void a_text_edit_changes_the_hash() {
        GoldenCase edited = new GoldenCase(SECOND.id(), SECOND.random(), SECOND.tickers(), SECOND.source(),
                "Nestle update (corrected)", SECOND.summary(), SECOND.expected());

        assertThat(hash(FIRST, edited)).isNotEqualTo(hash(FIRST, SECOND));
    }

    @Test
    void the_sample_flag_changes_the_hash() {
        GoldenCase moved = new GoldenCase(SECOND.id(), true, SECOND.tickers(), SECOND.source(), SECOND.headline(),
                SECOND.summary(), SECOND.expected());

        assertThat(hash(FIRST, moved)).isNotEqualTo(hash(FIRST, SECOND));
    }

    @Test
    void an_added_case_changes_count_and_hash() {
        GoldenSetFingerprint one = GoldenSetFingerprint.of(List.of(FIRST));
        GoldenSetFingerprint two = GoldenSetFingerprint.of(List.of(FIRST, SECOND));

        assertThat(two.cases()).isEqualTo(one.cases() + 1);
        assertThat(two.sha256()).isNotEqualTo(one.sha256());
    }

    private static String hash(GoldenCase... cases) {
        return GoldenSetFingerprint.of(List.of(cases)).sha256();
    }
}
