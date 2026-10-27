package com.briefwise.aiops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.aiops.AiopsFacade.BudgetView;
import com.briefwise.aiops.AiopsFacade.CostSummaryView;
import com.briefwise.aiops.AiopsFacade.GenerationEntry;
import com.briefwise.aiops.AiopsFacade.ReplayRequest;
import com.briefwise.aiops.domain.model.NoBudgetConfiguredException;
import com.briefwise.aiops.domain.model.UnpricedModelException;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import com.briefwise.shared.domain.model.TokenCount;
import com.briefwise.shared.infrastructure.web.CorrelationIdFilter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Uses {@code test-model} (1 USD / 2 USD per 1M tokens) and {@code it-feature} (0.01 USD/day) from application-test.yml. */
class AiopsFacadeIT extends AbstractIntegrationTest {

    private static final String FEATURE = "it-feature";
    private static final String MODEL = "test-model";

    @Autowired
    AiopsFacade facade;

    @Autowired
    JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void cleanUsage() {
        jdbc.update("DELETE FROM ai_daily_usage WHERE feature = ?", FEATURE);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generation_is_logged_with_cost_and_correlation_id() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "it-corr-1");

        UUID subject = UUID.randomUUID();
        UUID id = facade.recordGeneration(new GenerationEntry(FEATURE, MODEL, "v1", "Classify: ...",
                Optional.of("{\"sentiment\":\"NEUTRAL\"}"), new TokenCount(1_000, 500), Duration.ofMillis(420),
                false, Optional.of(subject), Optional.empty()));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM ai_generation_log WHERE id = ?", id);
        assertThat(row).containsEntry("feature", FEATURE)
                .containsEntry("model", MODEL)
                .containsEntry("prompt_version", "v1")
                .containsEntry("prompt_tokens", 1_000)
                .containsEntry("completion_tokens", 500)
                .containsEntry("latency_ms", 420L)
                .containsEntry("correlation_id", "it-corr-1")
                .containsEntry("is_fallback", false)
                .containsEntry("subject_id", subject);
        // 1,000 x 1 / 1M + 500 x 2 / 1M
        assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.002");
    }

    @Test
    void usable_answer_with_a_replay_request_is_found_again_and_the_newest_row_wins() {
        String input = "<news_item>\nheadline: " + UUID.randomUUID() + "\n</news_item>";
        ReplayRequest replay = new ReplayRequest(FEATURE, MODEL, "v1", input);
        facade.recordGeneration(new GenerationEntry(FEATURE, MODEL, "v1", "system\n\n---\n\n" + input,
                Optional.of("{\"answer\":\"old\"}"), new TokenCount(10, 5), Duration.ofMillis(100), false,
                Optional.empty(), Optional.of(replay)));
        UUID newest = facade.recordGeneration(new GenerationEntry(FEATURE, MODEL, "v1", "system\n\n---\n\n" + input,
                Optional.of("{\"answer\":\"new\"}"), new TokenCount(10, 5), Duration.ofMillis(100), false,
                Optional.empty(), Optional.of(replay)));

        assertThat(jdbc.queryForObject("SELECT replay_key FROM ai_generation_log WHERE id = ?", String.class, newest))
                .matches("[0-9a-f]{64}");
        // Same request with Windows line endings and extra spaces: the normalised key is the same (L7).
        assertThat(facade.findReplay(new ReplayRequest(FEATURE, MODEL, "v1", "  " + input.replace("\n", "\r\n"))))
                .contains("{\"answer\":\"new\"}");
        assertThat(facade.findReplay(new ReplayRequest(FEATURE, MODEL, "v2", input))).isEmpty();
    }

    @Test
    void row_without_a_replay_request_is_not_replayable() {
        UUID id = facade.recordGeneration(new GenerationEntry(FEATURE, MODEL, "v1", "Classify: ...",
                Optional.of("{}"), new TokenCount(10, 5), Duration.ofMillis(100), false, Optional.empty(),
                Optional.empty()));

        assertThat(jdbc.queryForMap("SELECT replay_key FROM ai_generation_log WHERE id = ?", id))
                .containsEntry("replay_key", null);
    }

    @Test
    void failed_call_is_logged_without_response_and_flagged_as_fallback() {
        UUID id = facade.recordGeneration(new GenerationEntry(FEATURE, MODEL, "v1", "Classify: ...",
                Optional.empty(), new TokenCount(0, 0), Duration.ofSeconds(30), true, Optional.empty(),
                Optional.empty()));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM ai_generation_log WHERE id = ?", id);
        assertThat(row.get("response")).isNull();
        assertThat(row).containsEntry("is_fallback", true).containsEntry("correlation_id", null)
                .containsEntry("subject_id", null);
    }

    @Test
    void cached_prefix_tokens_are_logged_and_priced_with_the_standard_cache_multipliers() {
        UUID id = facade.recordGeneration(new GenerationEntry(FEATURE, MODEL, "v1", "Classify: ...",
                Optional.of("{}"), new TokenCount(100, 50, 0, 1_000), Duration.ofMillis(300), false,
                Optional.empty(), Optional.empty()));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM ai_generation_log WHERE id = ?", id);
        assertThat(row).containsEntry("cache_write_tokens", 0).containsEntry("cache_read_tokens", 1_000);
        // test-model has no cache prices: read = 0.1 x input. 100 x 1 + 50 x 2 + 1,000 x 0.1 = 300 / 1M
        assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.0003");
    }

    @Test
    void usage_accumulates_until_the_gate_refuses_the_next_call() {
        assertThat(facade.ensureBudgetAvailable(FEATURE).remainingUsd()).isEqualByComparingTo("0.01");

        BudgetView afterFirst = facade.checkAndRecordUsage(FEATURE, MODEL, new TokenCount(4_000, 1_000));
        assertThat(afterFirst.spentUsd()).isEqualByComparingTo("0.006");
        assertThat(afterFirst.exhausted()).isFalse();
        facade.ensureBudgetAvailable(FEATURE);

        // The second call overshoots (0.012 > 0.01): recorded, not thrown; the gate refuses the third.
        BudgetView afterSecond = facade.checkAndRecordUsage(FEATURE, MODEL, new TokenCount(4_000, 1_000));
        assertThat(afterSecond.spentUsd()).isEqualByComparingTo("0.012");
        assertThat(afterSecond.exhausted()).isTrue();
        assertThat(afterSecond.remainingUsd()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> facade.ensureBudgetAvailable(FEATURE))
                .isInstanceOf(UsageLimitExceededException.class);
    }

    @Test
    void cost_summary_totals_calls_tokens_and_cost_per_feature() {
        facade.checkAndRecordUsage(FEATURE, MODEL, new TokenCount(1_000, 0));
        facade.checkAndRecordUsage(FEATURE, MODEL, new TokenCount(2_000, 500));

        CostSummaryView summary = facade.getCostSummary(today, today);

        assertThat(summary.byFeature()).singleElement().satisfies(f -> {
            assertThat(f.feature()).isEqualTo(FEATURE);
            assertThat(f.calls()).isEqualTo(2);
            assertThat(f.promptTokens()).isEqualTo(3_000);
            assertThat(f.completionTokens()).isEqualTo(500);
            assertThat(f.costUsd()).isEqualByComparingTo("0.004");
        });
        assertThat(summary.totalCalls()).isEqualTo(2);
        assertThat(summary.totalUsd()).isEqualByComparingTo("0.004");
    }

    @Test
    void unconfigured_model_or_feature_fails_closed() {
        assertThatThrownBy(() -> facade.checkAndRecordUsage(FEATURE, "unpriced-model", new TokenCount(1, 1)))
                .isInstanceOf(UnpricedModelException.class);
        assertThatThrownBy(() -> facade.ensureBudgetAvailable("no-budget-feature"))
                .isInstanceOf(NoBudgetConfiguredException.class);
    }

    @Test
    void configured_production_budget_is_bound() {
        assertThat(facade.ensureBudgetAvailable("news-classification").limitUsd()).isEqualByComparingTo("0.10");
    }
}
