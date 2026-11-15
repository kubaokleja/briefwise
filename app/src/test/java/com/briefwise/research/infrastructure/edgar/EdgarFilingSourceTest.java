package com.briefwise.research.infrastructure.edgar;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.FilingNotFoundException;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.model.FilingSection;
import com.briefwise.research.domain.model.FilingSourceUnavailableException;
import com.briefwise.research.domain.model.FilingType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test against a local WireMock server: no Spring context, no network. Fixtures are real Apple filings
 * fetched from EDGAR on 2026-11-15 and trimmed by hand (cover, table of contents, item headings with their first
 * paragraphs, the hidden {@code ix:header}); the submissions JSON keeps 8 of its rows.
 */
class EdgarFilingSourceTest {

    private static final String UA = "Briefwise test@example.com";
    private static final Cik APPLE = new Cik("320193");
    private static final String SUBMISSIONS = "/submissions/CIK0000320193.json";
    private static final String ARCHIVE = "/Archives/edgar/data/320193/";

    private static final FilingRef TEN_K = ref("0000320193-26-000026", FilingType.TEN_K, "2026-11-03",
            "aapl-20260926.htm", Set.of());
    private static final FilingRef TEN_Q = ref("0000320193-26-000020", FilingType.TEN_Q, "2026-07-31",
            "aapl-20260627.htm", Set.of());
    private static final FilingRef EARNINGS_8K = ref("0000320193-26-000024", FilingType.EIGHT_K, "2026-11-02",
            "aapl-20261102.htm", Set.of("2.02", "9.01"));
    private static final FilingRef NOTES_8K = ref("0001140361-26-042798", FilingType.EIGHT_K, "2026-11-09",
            "ef20085077_8k.htm", Set.of("8.01", "9.01"));

    private static WireMockServer server;
    private EdgarFilingSource source;

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        server.resetAll();
        source = sourceWithUserAgent(UA);
    }

    private static EdgarFilingSource sourceWithUserAgent(String userAgent) {
        EdgarProperties properties = new EdgarProperties(server.baseUrl(), server.baseUrl(), userAgent,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ofMillis(5), Duration.ZERO);
        return new EdgarFilingSource(properties, EdgarThrottle.real(Duration.ZERO));
    }

    private static FilingRef ref(String accession, FilingType type, String filedAt, String document,
                                 Set<String> items) {
        return new FilingRef(new AccessionNo(accession), APPLE, type, LocalDate.parse(filedAt), Optional.empty(),
                document, items);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = EdgarFilingSourceTest.class.getResourceAsStream("/edgar/" + name)) {
            return in.readAllBytes();
        }
    }

    private static String folder(FilingRef ref) {
        return ARCHIVE + ref.accessionNo().withoutDashes() + "/";
    }

    private static void stubDocument(FilingRef ref, String name) throws IOException {
        server.stubFor(get(urlPathEqualTo(folder(ref) + name))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                        .withBody(fixture(name))));
    }

    // --- list ------------------------------------------------------------------------------------------------------

    @Test
    void lists_only_ingested_forms_and_sends_the_user_agent() throws IOException {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).withHeader("User-Agent", equalTo(UA))
                .willReturn(okJson(new String(fixture("submissions-aapl.json"), StandardCharsets.UTF_8))));

        List<FilingRef> refs = source.list(APPLE);

        // Form 4, 8-K/A and 424B2 rows are dropped; the order is EDGAR's (newest first).
        assertThat(refs).extracting(r -> r.accessionNo().value()).containsExactly(
                "0001140361-26-042798", "0000320193-26-000026", "0000320193-26-000024", "0000320193-26-000020",
                "0001140361-26-006577");
        FilingRef earnings = refs.get(2);
        assertThat(earnings.type()).isEqualTo(FilingType.EIGHT_K);
        assertThat(earnings.cik()).isEqualTo(APPLE);
        assertThat(earnings.filedAt()).isEqualTo(LocalDate.parse("2026-11-02"));
        assertThat(earnings.reportDate()).contains(LocalDate.parse("2026-11-02"));
        assertThat(earnings.primaryDocument()).isEqualTo("aapl-20261102.htm");
        assertThat(earnings.items()).containsExactlyInAnyOrder("2.02", "9.01");
        assertThat(refs.get(1).items()).isEmpty();
    }

    @Test
    void empty_report_date_is_absent_and_a_malformed_row_is_dropped() {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).willReturn(okJson("""
                {"filings": {"recent": {
                  "accessionNumber": ["0000320193-26-000030", "not-an-accession"],
                  "filingDate": ["2026-11-10", "2026-11-11"],
                  "reportDate": ["", "2026-11-11"],
                  "form": ["8-K", "10-Q"],
                  "primaryDocument": ["a.htm", "b.htm"],
                  "items": [" 7.01 , 9.01 ", ""]
                }, "files": []}}""")));

        List<FilingRef> refs = source.list(APPLE);

        assertThat(refs).singleElement().satisfies(ref -> {
            assertThat(ref.reportDate()).isEmpty();
            assertThat(ref.items()).containsExactlyInAnyOrder("7.01", "9.01");
        });
    }

    @Test
    void response_without_recent_filings_is_unavailable() {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).willReturn(okJson("{\"cik\": \"320193\"}")));

        assertThatThrownBy(() -> source.list(APPLE))
                .isInstanceOf(FilingSourceUnavailableException.class)
                .hasMessageContaining("no recent filings");
    }

    // --- fetch -----------------------------------------------------------------------------------------------------

    @Test
    void ten_k_yields_the_four_kept_items_from_the_body_not_the_table_of_contents() throws IOException {
        stubDocument(TEN_K, "aapl-20260926.htm");

        FetchedFiling filing = source.fetch(TEN_K);

        assertThat(filing.sections()).extracting(FilingSection::itemId).containsExactly("1", "1A", "7", "7A");
        assertThat(filing.sections()).extracting(FilingSection::title).containsExactly("Business", "Risk Factors",
                "Management’s Discussion and Analysis of Financial Condition and Results of Operations",
                "Quantitative and Qualitative Disclosures About Market Risk");
        assertThat(filing.sections().getFirst().text()).contains("Company Background");
        assertThat(filing.sourceUrl()).isEqualTo(server.baseUrl() + folder(TEN_K) + "aapl-20260926.htm");
        assertThat(filing.extractorVersion()).isEqualTo(EdgarFilingSource.EXTRACTOR_VERSION);
        // Hidden inline-XBRL facts (dei:AmendmentFlag etc.) are not reader text.
        assertThat(filing.sections()).noneMatch(s -> s.text().contains("dei:"));
        server.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    void ten_q_items_are_part_qualified() throws IOException {
        stubDocument(TEN_Q, "aapl-20260627.htm");

        FetchedFiling filing = source.fetch(TEN_Q);

        assertThat(filing.sections()).extracting(FilingSection::itemId).containsExactly("I-2", "II-1A");
        assertThat(filing.sections()).extracting(FilingSection::title).containsExactly(
                "Management’s Discussion and Analysis of Financial Condition and Results of Operations",
                "Risk Factors");
    }

    @Test
    void earnings_eight_k_adds_the_press_release_found_through_the_filing_index() throws IOException {
        stubDocument(EARNINGS_8K, "aapl-20261102.htm");
        stubDocument(EARNINGS_8K, "0000320193-26-000024-index.htm");
        stubDocument(EARNINGS_8K, "a8-kex991q4202609262026.htm");

        FetchedFiling filing = source.fetch(EARNINGS_8K);

        assertThat(filing.sections()).extracting(FilingSection::itemId).containsExactly("2.02", "EX-99.1");
        assertThat(filing.sections().getFirst().text()).startsWith("On November 2, 2026, Apple Inc.");
        // The archive wraps exhibits in an SGML header (<DOCUMENT><TYPE>EX-99.1 ...): it must not become text.
        String release = filing.sections().get(1).text();
        assertThat(release).startsWith("Exhibit 99.1\nApple reports fourth quarter results");
        assertThat(release).doesNotContain("a8-kex991q4202609262026.htm");
        server.verify(3, anyRequestedFor(anyUrl()));
    }

    @Test
    void eight_k_without_ex_99_1_keeps_its_items_only() throws IOException {
        stubDocument(NOTES_8K, "ef20085077_8k.htm");
        stubDocument(NOTES_8K, "0001140361-26-042798-index.htm");

        FetchedFiling filing = source.fetch(NOTES_8K);

        assertThat(filing.sections()).extracting(FilingSection::itemId).containsExactly("8.01");
        server.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    void ex_99_1_with_a_path_instead_of_a_file_name_is_ignored() throws IOException {
        stubDocument(NOTES_8K, "ef20085077_8k.htm");
        server.stubFor(get(urlPathEqualTo(folder(NOTES_8K) + "0001140361-26-042798-index.htm")).willReturn(
                aResponse().withStatus(200).withBody("""
                        <table class="tableFile"><tr><td>2</td><td>EX-99.1</td>
                        <td><a href="/x">../../other/secret.htm</a></td><td>EX-99.1</td><td>1</td></tr></table>""")));

        FetchedFiling filing = source.fetch(NOTES_8K);

        assertThat(filing.sections()).extracting(FilingSection::itemId).containsExactly("8.01");
        server.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    void unknown_document_is_not_found() {
        server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> source.fetch(TEN_K))
                .isInstanceOf(FilingNotFoundException.class)
                .hasMessageContaining("0000320193-26-000026");
        server.verify(1, anyRequestedFor(anyUrl()));
    }

    // --- failures --------------------------------------------------------------------------------------------------

    @Test
    void rate_limit_is_retried() {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).inScenario("rate").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429)).willSetStateTo("open"));
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).inScenario("rate").whenScenarioStateIs("open")
                .willReturn(okJson("{\"filings\": {\"recent\": {\"accessionNumber\": [], \"filingDate\": [],"
                        + " \"form\": [], \"primaryDocument\": []}}}")));

        assertThat(source.list(APPLE)).isEmpty();
        server.verify(2, getRequestedFor(urlPathEqualTo(SUBMISSIONS)));
    }

    @Test
    void server_error_after_all_retries_is_unavailable() {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> source.list(APPLE))
                .isInstanceOf(FilingSourceUnavailableException.class)
                .hasMessageContaining("HTTP 503");
        server.verify(3, getRequestedFor(urlPathEqualTo(SUBMISSIONS))); // 1 attempt + 2 retries
    }

    @Test
    void timeout_is_retried_then_unavailable() {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).willReturn(okJson("{}").withFixedDelay(2000)));

        assertThatThrownBy(() -> source.list(APPLE))
                .isInstanceOf(FilingSourceUnavailableException.class)
                .hasMessageContaining("I/O error");
        server.verify(3, getRequestedFor(urlPathEqualTo(SUBMISSIONS)));
    }

    @Test
    void malformed_body_is_not_retried() {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).willReturn(okJson("[1, 2]")));

        assertThatThrownBy(() -> source.list(APPLE))
                .isInstanceOf(FilingSourceUnavailableException.class)
                .hasMessageContaining("unexpected response");
        server.verify(1, getRequestedFor(urlPathEqualTo(SUBMISSIONS)));
    }

    @Test
    void forbidden_is_a_configuration_error_and_not_retried() {
        server.stubFor(get(urlPathEqualTo(SUBMISSIONS)).willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> source.list(APPLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEC_USER_AGENT");
        server.verify(1, getRequestedFor(urlPathEqualTo(SUBMISSIONS)));
    }

    @Test
    void missing_user_agent_fails_without_calling_edgar() {
        EdgarFilingSource withoutUserAgent = sourceWithUserAgent(" ");

        assertThatThrownBy(() -> withoutUserAgent.list(APPLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEC_USER_AGENT");
        assertThatThrownBy(() -> withoutUserAgent.fetch(TEN_K))
                .isInstanceOf(IllegalStateException.class);
        server.verify(0, anyRequestedFor(anyUrl()));
    }
}
