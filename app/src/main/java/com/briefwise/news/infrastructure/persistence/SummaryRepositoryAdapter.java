package com.briefwise.news.infrastructure.persistence;

import com.briefwise.news.domain.model.PortfolioSummary;
import com.briefwise.news.domain.model.SummaryPoint;
import com.briefwise.news.domain.model.SummaryViolation;
import com.briefwise.news.domain.port.SummaryRepository;
import com.briefwise.shared.domain.model.Citation;
import com.briefwise.shared.domain.model.SourceType;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code news_portfolio_summaries} over plain JDBC: points and violations are JSONB documents, written and read
 * whole, never queried inside. The stored JSON shape is the {@code Stored*} records here, independent of the domain
 * records, so a domain change does not silently change what old rows mean.
 */
@Component
@Transactional(readOnly = true)
class SummaryRepositoryAdapter implements SummaryRepository {

    record StoredCitation(String type, String sourceId, String locator, String excerpt) {}

    record StoredPoint(String text, List<StoredCitation> citations) {}

    record StoredViolation(String rule, String offendingText) {}

    private static final TypeReference<List<StoredPoint>> POINTS = new TypeReference<>() {};
    private static final TypeReference<List<StoredViolation>> VIOLATIONS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    SummaryRepositoryAdapter(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<PortfolioSummary> findLatest(UUID portfolioId) {
        return jdbc.query("""
                SELECT id, portfolio_id, summary_date, generated_at, points::text AS points, is_fallback, model,
                       prompt_version, disclaimer, violations::text AS violations
                FROM news_portfolio_summaries
                WHERE portfolio_id = ?
                ORDER BY summary_date DESC
                LIMIT 1""", (rs, row) -> toSummary(rs), portfolioId).stream().findFirst();
    }

    @Override
    @Transactional
    public void save(PortfolioSummary s) {
        jdbc.update("""
                INSERT INTO news_portfolio_summaries (id, portfolio_id, summary_date, generated_at, points, is_fallback,
                                                      model, prompt_version, disclaimer, violations)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (portfolio_id, summary_date) DO UPDATE
                SET generated_at = EXCLUDED.generated_at, points = EXCLUDED.points,
                    is_fallback = EXCLUDED.is_fallback, model = EXCLUDED.model,
                    prompt_version = EXCLUDED.prompt_version, disclaimer = EXCLUDED.disclaimer,
                    violations = EXCLUDED.violations, updated_at = now()""",
                s.id(), s.portfolioId(), Date.valueOf(s.summaryDate()), Timestamp.from(s.generatedAt()),
                json.writeValueAsString(s.points().stream().map(SummaryRepositoryAdapter::stored).toList()),
                s.fallback(), s.model().orElse(null), s.promptVersion().orElse(null), s.disclaimer(),
                json.writeValueAsString(s.violations().stream()
                        .map(v -> new StoredViolation(v.rule(), v.offendingText())).toList()));
    }

    private PortfolioSummary toSummary(ResultSet rs) throws SQLException {
        List<SummaryPoint> points = json.readValue(rs.getString("points"), POINTS).stream()
                .map(p -> new SummaryPoint(p.text(), p.citations().stream()
                        .map(c -> new Citation(SourceType.valueOf(c.type()), c.sourceId(), c.locator(), c.excerpt()))
                        .toList()))
                .toList();
        List<SummaryViolation> violations = json.readValue(rs.getString("violations"), VIOLATIONS).stream()
                .map(v -> new SummaryViolation(v.rule(), v.offendingText())).toList();
        return new PortfolioSummary(rs.getObject("id", UUID.class), rs.getObject("portfolio_id", UUID.class),
                rs.getDate("summary_date").toLocalDate(), rs.getTimestamp("generated_at").toInstant(), points,
                rs.getBoolean("is_fallback"), Optional.ofNullable(rs.getString("model")),
                Optional.ofNullable(rs.getString("prompt_version")), rs.getString("disclaimer"), violations);
    }

    private static StoredPoint stored(SummaryPoint p) {
        return new StoredPoint(p.text(), p.citations().stream()
                .map(c -> new StoredCitation(c.type().name(), c.sourceId(), c.locator(), c.excerpt())).toList());
    }
}
