package com.briefwise.aiops.infrastructure.persistence;

import com.briefwise.aiops.domain.model.DailyUsage;
import com.briefwise.aiops.domain.model.NewGeneration;
import com.briefwise.aiops.domain.port.DailyUsageRepository;
import com.briefwise.aiops.domain.port.GenerationRepository;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter for both aiops ports. The log is a plain JPA insert; daily totals use SQL so the increment is
 * one atomic {@code INSERT ... ON CONFLICT DO UPDATE} (no read-modify-write race between concurrent callers).
 */
@Component
@Transactional(readOnly = true)
class AiopsRepositoryAdapter implements GenerationRepository, DailyUsageRepository {

    private static final String USAGE_COLUMNS =
            "usage_date, feature, call_count, prompt_tokens, completion_tokens, cost_usd";

    private final GenerationLogJpaRepository generations;
    private final JdbcTemplate jdbc;

    AiopsRepositoryAdapter(GenerationLogJpaRepository generations, JdbcTemplate jdbc) {
        this.generations = generations;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public UUID save(NewGeneration g, BigDecimal costUsd, Optional<String> correlationId) {
        GenerationLogEntity entity = new GenerationLogEntity(UUID.randomUUID(), g.feature(), g.model(),
                g.promptVersion(), g.prompt(), g.response().orElse(null), g.tokens().promptTokens(),
                g.tokens().completionTokens(), costUsd, g.latency().toMillis(), correlationId.orElse(null),
                g.fallback());
        return generations.save(entity).getId();
    }

    @Override
    public Optional<DailyUsage> find(LocalDate date, String feature) {
        return jdbc.query("SELECT " + USAGE_COLUMNS + " FROM ai_daily_usage WHERE usage_date = ? AND feature = ?",
                AiopsRepositoryAdapter::toUsage, date, feature).stream().findFirst();
    }

    @Override
    @Transactional
    public DailyUsage add(LocalDate date, String feature, TokenCount tokens, BigDecimal costUsd) {
        return jdbc.queryForObject("""
                INSERT INTO ai_daily_usage (usage_date, feature, call_count, prompt_tokens, completion_tokens, cost_usd)
                VALUES (?, ?, 1, ?, ?, ?)
                ON CONFLICT (usage_date, feature) DO UPDATE SET
                    call_count        = ai_daily_usage.call_count + 1,
                    prompt_tokens     = ai_daily_usage.prompt_tokens + EXCLUDED.prompt_tokens,
                    completion_tokens = ai_daily_usage.completion_tokens + EXCLUDED.completion_tokens,
                    cost_usd          = ai_daily_usage.cost_usd + EXCLUDED.cost_usd,
                    updated_at        = now()
                RETURNING\s""" + USAGE_COLUMNS,
                AiopsRepositoryAdapter::toUsage,
                date, feature, tokens.promptTokens(), tokens.completionTokens(), costUsd);
    }

    @Override
    public List<DailyUsage> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT " + USAGE_COLUMNS
                        + " FROM ai_daily_usage WHERE usage_date BETWEEN ? AND ? ORDER BY usage_date, feature",
                AiopsRepositoryAdapter::toUsage, from, to);
    }

    private static DailyUsage toUsage(ResultSet rs, int row) throws SQLException {
        return new DailyUsage(rs.getObject("usage_date", LocalDate.class), rs.getString("feature"),
                rs.getInt("call_count"), rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"),
                rs.getBigDecimal("cost_usd"));
    }
}
