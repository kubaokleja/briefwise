package com.briefwise.research.infrastructure.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.FilingNotFoundException;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.model.FilingSection;
import com.briefwise.research.domain.model.FilingType;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Writer and reader against a temporary directory: no Spring context. */
class FilingSnapshotRoundTripTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Cik APPLE = new Cik("320193");

    private static final FetchedFiling TEN_K = new FetchedFiling(
            new FilingRef(new AccessionNo("0000320193-26-000026"), APPLE, FilingType.TEN_K,
                    LocalDate.parse("2026-11-03"), Optional.of(LocalDate.parse("2026-09-26")), "aapl-20260926.htm",
                    Set.of()),
            "https://www.sec.gov/Archives/edgar/data/320193/000032019326000026/aapl-20260926.htm", "html-1/sections-2",
            List.of(new FilingSection("1", "Business", "The Company designs smartphones.\n\nIt sells them."),
                    new FilingSection("1A", "Risk Factors", "Net sales | 2026 | 2025\niPhone | $200 | $190 – “quoted”")));
    private static final FetchedFiling EIGHT_K = new FetchedFiling(
            new FilingRef(new AccessionNo("0000320193-26-000024"), APPLE, FilingType.EIGHT_K,
                    LocalDate.parse("2026-11-02"), Optional.empty(), "aapl-20261102.htm", Set.of("9.01", "2.02")),
            "https://www.sec.gov/x/aapl-20261102.htm", "html-1/sections-2",
            List.of(new FilingSection("2.02", "Results", "Press release attached."),
                    new FilingSection("EX-99.1", "Exhibit 99.1", "Apple reports results.")));
    private static final FetchedFiling MSFT_TEN_K = new FetchedFiling(
            new FilingRef(new AccessionNo("0001193125-26-323660"), new Cik("789019"), FilingType.TEN_K,
                    LocalDate.parse("2026-07-29"), Optional.empty(), "msft-20260630.htm", Set.of()),
            "https://www.sec.gov/x/msft-20260630.htm", "html-1/sections-2",
            List.of(new FilingSection("1", "BUSINESS", "Microsoft is a technology company.")));

    @TempDir
    Path dir;

    private JsonFilingSnapshotWriter writer;
    private SnapshotFilingSource source;

    @BeforeEach
    void setUp() {
        JsonMapper json = JsonMapper.builder().build();
        writer = new JsonFilingSnapshotWriter(new FilingSnapshotProperties("edgar", dir.toString()), json);
        source = new SnapshotFilingSource(new FilingSnapshotProperties("snapshot", dir.toString()), json);
    }

    private Path file(String ticker, String accession) {
        return dir.resolve(ticker).resolve(accession + ".json");
    }

    @Test
    void written_filings_read_back_equal() {
        writer.write(AAPL, TEN_K);
        writer.write(AAPL, EIGHT_K);
        writer.write(MSFT, MSFT_TEN_K);

        assertThat(file("AAPL", "0000320193-26-000026")).isRegularFile();
        assertThat(source.list(APPLE)).containsExactlyInAnyOrder(TEN_K.ref(), EIGHT_K.ref());
        assertThat(source.fetch(TEN_K.ref())).isEqualTo(TEN_K);
        assertThat(source.fetch(EIGHT_K.ref())).isEqualTo(EIGHT_K);
        assertThat(source.fetch(EIGHT_K.ref()).ref().reportDate()).isEmpty();
        assertThat(source.fetch(TEN_K.ref()).contentSha256()).isEqualTo(TEN_K.contentSha256());
    }

    @Test
    void the_same_filing_gives_the_same_bytes_with_lf_line_endings() throws IOException {
        writer.write(AAPL, EIGHT_K);
        byte[] first = Files.readAllBytes(file("AAPL", "0000320193-26-000024"));
        writer.write(AAPL, EIGHT_K);
        byte[] second = Files.readAllBytes(file("AAPL", "0000320193-26-000024"));

        assertThat(second).isEqualTo(first);
        String text = new String(first, StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("\r").endsWith("}\n").contains("\"formatVersion\" : 1");
        assertThat(text).contains("\"items\" : [\n    \"2.02\",\n    \"9.01\"\n  ]"); // sorted, whatever the set order
        assertThat(text).contains("\"reportDate\" : null");
        try (var leftovers = Files.list(dir.resolve("AAPL"))) {
            assertThat(leftovers).hasSize(1); // no temporary file left behind
        }
    }

    @Test
    void unknown_accession_is_not_found() {
        writer.write(AAPL, TEN_K);

        assertThatThrownBy(() -> source.fetch(EIGHT_K.ref()))
                .isInstanceOf(FilingNotFoundException.class)
                .hasMessageContaining("0000320193-26-000024");
    }

    @Test
    void an_edited_section_text_fails_the_content_check() throws IOException {
        writer.write(AAPL, TEN_K);
        Path file = file("AAPL", "0000320193-26-000026");
        Files.writeString(file, Files.readString(file).replace("designs smartphones", "designs cars"));

        assertThatThrownBy(() -> source.fetch(TEN_K.ref()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was changed");
    }

    @Test
    void malformed_file_fails_with_its_path() throws IOException {
        Files.createDirectories(dir.resolve("AAPL"));
        Files.writeString(file("AAPL", "0000320193-26-000026"), "{ not json");

        assertThatThrownBy(() -> source.list(APPLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0000320193-26-000026.json");
    }

    @Test
    void missing_snapshot_directory_lists_nothing() {
        SnapshotFilingSource empty = new SnapshotFilingSource(
                new FilingSnapshotProperties("snapshot", dir.resolve("absent").toString()), JsonMapper.builder().build());

        assertThat(empty.list(APPLE)).isEmpty();
        assertThatThrownBy(() -> empty.fetch(TEN_K.ref())).isInstanceOf(FilingNotFoundException.class);
    }

    @Test
    void other_files_in_the_directory_are_ignored() throws IOException {
        writer.write(AAPL, TEN_K);
        Files.writeString(dir.resolve("README.md"), "snapshot");
        Files.writeString(dir.resolve("AAPL").resolve("notes.txt"), "x");

        assertThat(source.list(APPLE)).containsExactly(TEN_K.ref());
    }
}
