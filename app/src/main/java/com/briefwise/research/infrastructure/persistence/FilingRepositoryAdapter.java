package com.briefwise.research.infrastructure.persistence;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.Filing;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.model.FilingSection;
import com.briefwise.research.domain.model.FilingType;
import com.briefwise.research.domain.model.ScreenedSection;
import com.briefwise.research.domain.model.Screening;
import com.briefwise.research.domain.port.FilingRepository;
import com.briefwise.shared.domain.model.Ticker;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code research_filings} and {@code research_filing_sections} (V18) over plain JDBC. A filing and its sections are
 * written together in one transaction; {@link #replace} keeps the stored filing id and swaps every section, so later
 * chunks (slice 3, {@code ON DELETE CASCADE}) never point at text that is gone.
 */
@Component
@Transactional(readOnly = true)
class FilingRepositoryAdapter implements FilingRepository {

    private final JdbcTemplate jdbc;

    FilingRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findContentSha256(AccessionNo accessionNo) {
        return jdbc.queryForList("SELECT content_sha256 FROM research_filings WHERE accession_no = ?", String.class,
                accessionNo.value()).stream().findFirst();
    }

    @Override
    public Optional<Filing> findByAccession(AccessionNo accessionNo) {
        return jdbc.query("""
                SELECT id, accession_no, ticker, cik, form, filed_at, period_of_report, primary_document, items,
                       source_url, content_sha256, extractor_version
                FROM research_filings
                WHERE accession_no = ?""", (rs, row) -> toFiling(rs), accessionNo.value()).stream().findFirst();
    }

    @Override
    @Transactional
    public void insert(Filing filing) {
        FilingRef ref = filing.ref();
        jdbc.update("""
                INSERT INTO research_filings (id, accession_no, ticker, cik, form, filed_at, period_of_report,
                                              primary_document, items, source_url, content_sha256, extractor_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                filing.id(), ref.accessionNo().value(), filing.ticker().symbol(), ref.cik().value(), ref.type().form(),
                Date.valueOf(ref.filedAt()), ref.reportDate().map(Date::valueOf).orElse(null), ref.primaryDocument(),
                sorted(ref.items()), filing.sourceUrl(), filing.contentSha256(), filing.extractorVersion());
        insertSections(filing.id(), filing.sections());
    }

    @Override
    @Transactional
    public void replace(Filing filing) {
        FilingRef ref = filing.ref();
        List<UUID> ids = jdbc.queryForList("""
                UPDATE research_filings
                SET ticker = ?, cik = ?, form = ?, filed_at = ?, period_of_report = ?, primary_document = ?, items = ?,
                    source_url = ?, content_sha256 = ?, extractor_version = ?, updated_at = now()
                WHERE accession_no = ?
                RETURNING id""", UUID.class,
                filing.ticker().symbol(), ref.cik().value(), ref.type().form(), Date.valueOf(ref.filedAt()),
                ref.reportDate().map(Date::valueOf).orElse(null), ref.primaryDocument(), sorted(ref.items()),
                filing.sourceUrl(), filing.contentSha256(), filing.extractorVersion(), ref.accessionNo().value());
        if (ids.isEmpty()) {
            throw new IllegalStateException("No stored filing to replace: " + ref.accessionNo().value());
        }
        UUID storedId = ids.getFirst();
        jdbc.update("DELETE FROM research_filing_sections WHERE filing_id = ?", storedId);
        insertSections(storedId, filing.sections());
    }

    private void insertSections(UUID filingId, List<ScreenedSection> sections) {
        for (int ordinal = 0; ordinal < sections.size(); ordinal++) {
            ScreenedSection s = sections.get(ordinal);
            jdbc.update("""
                    INSERT INTO research_filing_sections (id, filing_id, ordinal, item_id, title, text,
                                                          injection_suspected, matched_rules)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    s.id(), filingId, ordinal, s.section().itemId(), s.section().title(), s.section().text(),
                    s.screening().suspected(), s.screening().matchedRules().toArray(String[]::new));
        }
    }

    private Filing toFiling(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String form = rs.getString("form");
        FilingType type = FilingType.fromForm(form)
                .orElseThrow(() -> new IllegalStateException("Unknown stored form: " + form));
        Date period = rs.getDate("period_of_report");
        FilingRef ref = new FilingRef(new AccessionNo(rs.getString("accession_no")), new Cik(rs.getString("cik")),
                type, rs.getDate("filed_at").toLocalDate(), Optional.ofNullable(period).map(Date::toLocalDate),
                rs.getString("primary_document"), Set.of(strings(rs.getArray("items"))));
        return new Filing(id, new Ticker(rs.getString("ticker")), ref, rs.getString("source_url"),
                rs.getString("extractor_version"), rs.getString("content_sha256"), sections(id));
    }

    private List<ScreenedSection> sections(UUID filingId) {
        return jdbc.query("""
                SELECT id, item_id, title, text, injection_suspected, matched_rules
                FROM research_filing_sections
                WHERE filing_id = ?
                ORDER BY ordinal""", (rs, row) -> new ScreenedSection(rs.getObject("id", UUID.class),
                new FilingSection(rs.getString("item_id"), rs.getString("title"), rs.getString("text")),
                new Screening(rs.getBoolean("injection_suspected"), List.of(strings(rs.getArray("matched_rules"))))),
                filingId);
    }

    private static String[] strings(Array array) throws SQLException {
        return (String[]) array.getArray();
    }

    /** Items are a set; sorted so the stored array is stable across runs. */
    private static String[] sorted(Set<String> items) {
        return items.stream().sorted().toArray(String[]::new);
    }
}
