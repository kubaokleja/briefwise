package com.briefwise;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starting the context runs every migration against an empty pgvector database and then Hibernate's
 * {@code ddl-auto: validate}; if a migration and an entity disagree, this context fails to start.
 */
class FlywayBaselineIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    @Test
    void schema_validation_is_on_and_all_migrations_applied() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(applied).isEqualTo(18);
    }

    @Test
    void vector_and_pg_trgm_extensions_are_enabled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(extensions).contains("vector", "pg_trgm");
    }

    @Test
    void three_synthetic_portfolios_are_seeded_and_weights_sum_to_one() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clients", Integer.class)).isEqualTo(3);
        List<BigDecimal> sums = jdbc.queryForList(
                "SELECT sum(weight) FROM positions GROUP BY portfolio_id", BigDecimal.class);
        assertThat(sums).hasSize(3).allSatisfy(sum -> assertThat(sum).isEqualByComparingTo("1"));
    }

    @Test
    void every_stock_has_a_ten_digit_cik_and_no_etf_has_one() {
        Integer stocksWithoutCik = jdbc.queryForObject(
                "SELECT count(*) FROM instruments WHERE instrument_type = 'STOCK' AND (cik IS NULL OR cik !~ '^[0-9]{10}$')",
                Integer.class);
        Integer etfsWithCik = jdbc.queryForObject(
                "SELECT count(*) FROM instruments WHERE instrument_type = 'ETF' AND cik IS NOT NULL", Integer.class);
        assertThat(stocksWithoutCik).isZero();
        assertThat(etfsWithCik).isZero();
    }

    @Test
    void every_seeded_position_has_an_instrument() {
        Integer orphans = jdbc.queryForObject(
                "SELECT count(*) FROM positions p WHERE NOT EXISTS (SELECT 1 FROM instruments i WHERE i.ticker = p.ticker)",
                Integer.class);
        assertThat(orphans).isZero();
    }
}
