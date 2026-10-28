package com.briefwise.news.infrastructure.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.briefwise.news.domain.model.EvalLabels;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvGoldenSetTest {

    private static final String HEADER = "id,sample,tickers,source,headline,summary,sentiment,relevance,event_type\n";

    @TempDir
    Path dir;

    private CsvGoldenSet golden(String content) throws IOException {
        Path file = dir.resolve("golden.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return new CsvGoldenSet(new NewsEvalProperties(file, dir, 3.0));
    }

    @Test
    void reads_the_fixture_with_quotes_commas_and_line_breaks() {
        List<GoldenCase> cases = new CsvGoldenSet(new NewsEvalProperties(
                Path.of("src/test/resources/eval/golden-fixture.csv"), dir, 3.0)).load();

        assertThat(cases).hasSize(6);
        assertThat(cases.getFirst().summary()).isEqualTo("iPhone sales rose 8%, ahead of forecasts.");
        assertThat(cases.get(1).summary()).isEqualTo("A \"watch list\" of large caps.");
        assertThat(cases.get(2).summary()).isEqualTo("Plaintiffs filed in Missouri.\nA second suit is expected.");
        assertThat(cases.get(2).expected())
                .isEqualTo(new EvalLabels(Sentiment.NEGATIVE, Relevance.HIGH, EventType.LITIGATION));
        assertThat(cases.get(3).tickers()).containsExactlyInAnyOrder(new Ticker("KO"), new Ticker("PEP"));
        assertThat(cases.get(4).summary()).isEmpty();
        assertThat(cases).filteredOn(GoldenCase::random).hasSize(3);
    }

    @Test
    void reads_utf8_with_a_bom_and_crlf_line_ends() throws IOException {
        String row = "0b5c6a51-0001-4000-8000-000000000001,random,AAPL,Wire,Apple’s week — in review,,"
                + "NEUTRAL,LOW,OTHER\r\n";

        List<GoldenCase> cases = golden("﻿" + HEADER.replace("\n", "\r\n") + row).load();

        assertThat(cases).singleElement().satisfies(c -> {
            assertThat(c.headline()).isEqualTo("Apple’s week — in review");
            assertThat(c.tickers()).isEqualTo(Set.of(new Ticker("AAPL")));
        });
    }

    @Test
    void a_missing_file_says_how_to_decrypt_it() {
        CsvGoldenSet missing = new CsvGoldenSet(new NewsEvalProperties(dir.resolve("nope.csv"), dir, 3.0));

        assertThatThrownBy(missing::load).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt-golden.sh");
    }

    @Test
    void an_unknown_label_names_the_row() throws IOException {
        String bad = "0b5c6a51-0001-4000-8000-000000000001,random,AAPL,Wire,H,,MIXED,LOW,OTHER\n";

        assertThatThrownBy(() -> golden(HEADER + bad).load()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("row 2").hasMessageContaining("MIXED");
    }

    @Test
    void a_missing_column_is_rejected() throws IOException {
        assertThatThrownBy(() -> golden("id,sample,headline\n").load()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event_type");
    }

    @Test
    void an_unterminated_quote_is_rejected() {
        assertThatThrownBy(() -> CsvGoldenSet.parse("a,\"b\n")).isInstanceOf(IllegalStateException.class);
    }
}
