package com.briefwise.market.infrastructure.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvPriceSnapshotSourceTest {

    @TempDir
    Path dir;

    private CsvPriceSnapshotSource source() {
        return new CsvPriceSnapshotSource(new PriceSnapshotProperties(dir.toString(), false));
    }

    private void file(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void reads_one_ticker_per_csv_file_and_ignores_other_files() throws IOException {
        file("AAPL.csv", "date,close,volume\n2026-10-14,100.5,1000\n2026-10-15,101.25,2000\n");
        file("MSFT.csv", "date,close,volume\n2026-10-15,400,3000\n");
        file("README.txt", "not a snapshot file");

        Map<Ticker, List<DailyPrice>> snapshot = source().read();

        assertThat(snapshot).containsOnlyKeys(new Ticker("AAPL"), new Ticker("MSFT"));
        assertThat(snapshot.get(new Ticker("AAPL"))).hasSize(2);
        DailyPrice first = snapshot.get(new Ticker("AAPL")).getFirst();
        assertThat(first.date()).isEqualTo(LocalDate.parse("2026-10-14"));
        assertThat(first.close()).isEqualByComparingTo("100.5");
        assertThat(first.volume()).isEqualTo(1000);
    }

    @Test
    void a_missing_directory_is_an_error_that_names_the_path() {
        CsvPriceSnapshotSource missing =
                new CsvPriceSnapshotSource(new PriceSnapshotProperties(dir.resolve("nope").toString(), false));

        assertThatThrownBy(missing::read).isInstanceOf(IllegalStateException.class).hasMessageContaining("nope");
    }

    @Test
    void a_wrong_header_is_rejected() throws IOException {
        file("AAPL.csv", "day,price,qty\n2026-10-14,100,1\n");

        assertThatThrownBy(() -> source().read())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AAPL.csv")
                .hasMessageContaining("header");
    }

    @Test
    void a_bad_row_is_reported_with_file_and_line() throws IOException {
        file("AAPL.csv", "date,close,volume\n2026-10-14,100,1\n2026-10-15,abc,1\n");

        assertThatThrownBy(() -> source().read())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AAPL.csv:3");
    }

    @Test
    void a_non_positive_close_is_rejected_by_the_domain() throws IOException {
        file("AAPL.csv", "date,close,volume\n2026-10-14,0,1\n");

        assertThatThrownBy(() -> source().read())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AAPL.csv:2");
    }
}
