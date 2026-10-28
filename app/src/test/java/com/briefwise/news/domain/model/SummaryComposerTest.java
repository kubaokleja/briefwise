package com.briefwise.news.domain.model;

import static com.briefwise.news.SummaryFixtures.NOW;
import static com.briefwise.news.SummaryFixtures.relevant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.shared.domain.model.Citation;
import com.briefwise.shared.domain.model.SourceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SummaryComposerTest {

    private final RelevantNews apple = relevant("Apple misses revenue estimates", Relevance.HIGH, NOW, "AAPL", "0.1");
    private final RelevantNews msft = relevant("Microsoft wins cloud deal", Relevance.MEDIUM, NOW, "MSFT", "0.2");

    @Test
    void draft_ids_become_citations_with_url_and_headline_from_the_selection() {
        SummaryDraft draft = new SummaryDraft(List.of(new SummaryDraft.Point("AAPL missed estimates.",
                List.of(apple.news().id()))), "claude-haiku-5-5", "v1");

        List<SummaryPoint> points = SummaryComposer.cite(draft, List.of(apple, msft));

        assertThat(points).singleElement().satisfies(p -> {
            assertThat(p.text()).isEqualTo("AAPL missed estimates.");
            assertThat(p.citations()).containsExactly(new Citation(SourceType.NEWS, apple.news().id().toString(),
                    apple.news().url(), "Apple misses revenue estimates"));
        });
    }

    @Test
    void a_cited_id_outside_the_selection_is_a_bug() {
        SummaryDraft draft = new SummaryDraft(List.of(new SummaryDraft.Point("Made up.", List.of(UUID.randomUUID()))),
                "m", "v1");

        assertThatThrownBy(() -> SummaryComposer.cite(draft, List.of(apple)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void template_has_one_cited_point_per_item_with_tickers_headline_and_labels() {
        List<SummaryPoint> points = SummaryComposer.template(List.of(apple, msft));

        assertThat(points).extracting(SummaryPoint::text).containsExactly(
                "AAPL: Apple misses revenue estimates (negative, earnings)",
                "MSFT: Microsoft wins cloud deal (negative, earnings)");
        assertThat(points).allSatisfy(p -> assertThat(p.citations()).hasSize(1));
    }
}
