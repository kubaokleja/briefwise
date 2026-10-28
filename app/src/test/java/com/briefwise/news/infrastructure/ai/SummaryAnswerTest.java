package com.briefwise.news.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.news.domain.model.SummaryDraft;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

class SummaryAnswerTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static SummaryAnswer answer(SummaryAnswer.Point... points) {
        return new SummaryAnswer(List.of(points));
    }

    private static SummaryAnswer.Point point(String text, String... ids) {
        return new SummaryAnswer.Point(text, List.of(ids));
    }

    @Test
    void schema_requires_points_with_text_and_news_ids() {
        String schema = new BeanOutputConverter<>(SummaryAnswer.class).getJsonSchema();

        assertThat(schema).contains("\"points\"").contains("\"text\"").contains("\"newsIds\"")
                .contains("copied exactly");
    }

    @Test
    void valid_answer_becomes_a_draft_with_deduplicated_ids() {
        SummaryDraft draft = answer(point("  AAPL missed estimates. ", A.toString(), A.toString(), B.toString()))
                .toDraft(Set.of(A, B), 5, "claude-haiku-5-5", "v1");

        assertThat(draft.points()).singleElement().satisfies(p -> {
            assertThat(p.text()).isEqualTo("AAPL missed estimates.");
            assertThat(p.newsIds()).containsExactly(A, B);
        });
        assertThat(draft.model()).isEqualTo("claude-haiku-5-5");
    }

    @Test
    void an_id_that_was_not_in_the_request_is_invalid() {
        assertThatThrownBy(() -> answer(point("Made up.", UUID.randomUUID().toString()))
                .toDraft(Set.of(A), 5, "m", "v1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not in the request");
    }

    @Test
    void a_malformed_id_is_invalid() {
        assertThatThrownBy(() -> answer(point("Text.", "item-1")).toDraft(Set.of(A), 5, "m", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_point_without_ids_or_text_is_invalid() {
        assertThatThrownBy(() -> answer(point("Uncited.")).toDraft(Set.of(A), 5, "m", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> answer(point(" ", A.toString())).toDraft(Set.of(A), 5, "m", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void no_points_or_too_many_points_are_invalid() {
        assertThatThrownBy(() -> new SummaryAnswer(List.of()).toDraft(Set.of(A), 5, "m", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
        List<SummaryAnswer.Point> six = Collections.nCopies(6, point("Text.", A.toString()));
        assertThatThrownBy(() -> new SummaryAnswer(six).toDraft(Set.of(A), 5, "m", "v1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most 5");
    }
}
