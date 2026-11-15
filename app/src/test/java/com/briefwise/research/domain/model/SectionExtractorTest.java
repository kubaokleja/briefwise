package com.briefwise.research.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Synthetic text shaped like {@code HtmlText} output, plus real excerpts of that output for the held issuers whose
 * layout broke version 1 ({@code test/resources/research/}). Real HTML is covered by the EDGAR adapter tests
 * ({@code test/resources/edgar/}).
 */
class SectionExtractorTest {

    private final SectionExtractor extractor = new SectionExtractor();

    private static final String TEN_K = String.join("\n",
            "APPLE INC.",
            "FORM 10-K",
            "TABLE OF CONTENTS",
            "PART I",
            "Item 1. | Business | 1",
            "Item 1A. | Risk Factors | 5",
            "Item 1B. | Unresolved Staff Comments | 17",
            "PART II",
            "Item 7. | Management's Discussion and Analysis | 20",
            "Item 7A. | Quantitative and Qualitative Disclosures About Market Risk | 28",
            "Item 8. | Financial Statements | 29",
            "",
            "PART I",
            "Item 1.   Business",
            "The Company designs smartphones.",
            "",
            "",
            "It sells them worldwide.",
            "Item 1A.",
            "Risk Factors",
            "Supply chains may fail. As noted in Item 7, results vary by quarter.",
            "Item 1B. Unresolved Staff Comments",
            "None.",
            "PART II",
            "ITEM 7. MANAGEMENT'S DISCUSSION AND ANALYSIS",
            "Net sales | 2026 | 2025",
            "iPhone | $200,000 | $190,000",
            "Item 7A. Quantitative and Qualitative Disclosures About Market Risk",
            "Interest rate risk is low.",
            "Item 8. Financial Statements",
            "Balance sheet follows.");

    @Test
    void ten_k_items_come_from_the_body_not_the_table_of_contents() {
        List<FilingSection> sections = extractor.extract(FilingType.TEN_K, TEN_K);

        assertThat(sections).extracting(FilingSection::itemId).containsExactly("1", "1A", "7", "7A");
        assertThat(sections.get(0)).isEqualTo(new FilingSection("1", "Business",
                "The Company designs smartphones.\n\nIt sells them worldwide."));
        assertThat(sections.get(1).title()).isEqualTo("Risk Factors");
        assertThat(sections.get(1).text()).startsWith("Supply chains may fail.").doesNotContain("Unresolved");
        assertThat(sections.get(2).text()).isEqualTo("Net sales | 2026 | 2025\niPhone | $200,000 | $190,000");
        assertThat(sections.get(3).text()).isEqualTo("Interest rate risk is low.");
    }

    @Test
    void a_long_prose_line_starting_with_item_is_not_a_heading() {
        String prose = "Item 7 of this report discusses " + "results in detail ".repeat(15);
        String text = String.join("\n", "Item 1A. Risk Factors", "Risks.", prose, "More risks.",
                "Item 1B. Unresolved Staff Comments", "None.");

        List<FilingSection> sections = extractor.extract(FilingType.TEN_K, text);

        assertThat(sections).extracting(FilingSection::itemId).containsExactly("1A");
        assertThat(sections.get(0).text()).contains("More risks.");
    }

    @Test
    void ten_q_items_are_qualified_by_part() {
        String text = String.join("\n",
                "PART I - FINANCIAL INFORMATION",
                "Item 1. Financial Statements",
                "Statements.",
                "Item 2. Management's Discussion and Analysis",
                "Revenue fell 5% on lower demand.",
                "Item 3. Quantitative and Qualitative Disclosures About Market Risk",
                "No change.",
                "PART II - OTHER INFORMATION",
                "Item 1. Legal Proceedings",
                "None.",
                "Item 1A. Risk Factors",
                "No material changes from the 10-K.",
                "Item 2. Unregistered Sales of Equity Securities",
                "Buybacks table.");

        List<FilingSection> sections = extractor.extract(FilingType.TEN_Q, text);

        assertThat(sections).containsExactly(
                new FilingSection("I-2", "Management's Discussion and Analysis", "Revenue fell 5% on lower demand."),
                new FilingSection("II-1A", "Risk Factors", "No material changes from the 10-K."));
    }

    @Test
    void eight_k_items_use_the_dotted_numbers() {
        String text = String.join("\n",
                "FORM 8-K",
                "Item 2.02 Results of Operations and Financial Condition.",
                "On July 30 the Company issued a press release, attached as Exhibit 99.1.",
                "Item 9.01 Financial Statements and Exhibits.",
                "99.1 Press release");

        assertThat(extractor.extract(FilingType.EIGHT_K, text)).containsExactly(new FilingSection("2.02",
                "Results of Operations and Financial Condition.",
                "On July 30 the Company issued a press release, attached as Exhibit 99.1."));
    }

    @Test
    void the_exhibit_is_one_section_and_an_empty_one_is_dropped() {
        assertThat(extractor.exhibit991("Apple reports results\n\n\nRevenue $90B"))
                .contains(new FilingSection(SectionPolicy.EX_99_1, "Exhibit 99.1",
                        "Apple reports results\n\nRevenue $90B"));
        assertThat(extractor.exhibit991(" \n \n")).isEmpty();
    }

    @Test
    void missing_items_and_empty_bodies_are_left_out() {
        String text = String.join("\n", "Item 1A. Risk Factors", "", "Item 1B. Unresolved Staff Comments", "None.");
        assertThat(extractor.extract(FilingType.TEN_K, text)).isEmpty();
    }

    // --- page furniture (version 2) -------------------------------------------------------------------------------

    @Test
    void running_page_headers_are_dropped_and_are_no_boundaries() {
        String text = String.join("\n",
                "PART I", "Item 1. | Business | 3", "Item 1A. | Risk Factors | 14", "Item 7A. | Market Risk | 49",
                "PART I", "ITEM 1. BUSINESS", "Gaming grows.", "4",
                "PART I", "Item 1", "Cloud grows.", "5",
                "PART I", "Item 1", "Search grows.", "6",
                "PART I", "Item 1", "ITEM 1A. RISK FACTORS", "Competition is intense.",
                "PART II", "Item 7A", "ITEM 7A. QUANTITATIVE AND QUALITATIVE DISCLOSURES ABOUT MARKET RISK",
                "Rates may rise.", "50",
                "PART II", "Item 7A", "Currencies may move.",
                "ITEM 8. FINANCIAL STATEMENTS", "Balance sheet.");

        List<FilingSection> sections = extractor.extract(FilingType.TEN_K, text);

        assertThat(sections).containsExactly(
                new FilingSection("1", "BUSINESS", "Gaming grows.\nCloud grows.\nSearch grows."),
                new FilingSection("1A", "RISK FACTORS", "Competition is intense."),
                // "Item 7A" appears only twice, but in the shape of the running "Item 1": still a page header.
                new FilingSection("7A", "QUANTITATIVE AND QUALITATIVE DISCLOSURES ABOUT MARKET RISK",
                        "Rates may rise.\nCurrencies may move."));
    }

    @Test
    void a_running_part_header_still_sets_the_part_of_ten_q_items() {
        String text = String.join("\n",
                "PART I", "Item 2", "PART I", "Item 2", "PART I", "Item 2",
                "PART II", "Item 1A", "PART II", "Item 1A",
                "PART I", "ITEM 2. MD&A", "Revenue rose.",
                "PART II", "Item 1A", "ITEM 1A. RISK FACTORS", "New risks.",
                "PART II", "Item 1A", "More risks.",
                "ITEM 6. EXHIBITS", "List.");

        assertThat(extractor.extract(FilingType.TEN_Q, text)).containsExactly(
                new FilingSection("I-2", "MD&A", "Revenue rose."),
                new FilingSection("II-1A", "RISK FACTORS", "New risks.\nMore risks."));
    }

    @Test
    void page_numbers_footers_and_contents_links_are_dropped() {
        String text = String.join("\n",
                "Item 1A. Risk Factors",
                "Supply may fail.", "Apple Inc. | 2026 Form 10-K | 12", "- 13 -",
                "Demand may fall.", "Apple Inc. | 2026 Form 10-K | 13", "Page 14", "Table of Contents",
                "Rates may rise.", "Apple Inc. | 2026 Form 10-K | 14",
                "Table of Contents | Financial Table of Contents",
                "Item 1B. Unresolved Staff Comments", "None.");

        assertThat(extractor.extract(FilingType.TEN_K, text)).containsExactly(new FilingSection("1A",
                "Risk Factors", "Supply may fail.\nDemand may fall.\nRates may rise."));
    }

    @Test
    void repeated_table_rows_ending_in_a_number_are_kept() {
        String text = String.join("\n",
                "Item 7. MD&A",
                "Segment | Units", "Americas | 12", "Americas | 12", "Americas | 12", // same number: not a footer
                "Total | 30", "Total | 12", "Total | 45",                           // not rising: not a footer
                "Net sales | $ | 29,789",
                "Item 8. Financial Statements", "Statements.");

        assertThat(extractor.extract(FilingType.TEN_K, text).getFirst().text()).isEqualTo(String.join("\n",
                "Segment | Units", "Americas | 12", "Americas | 12", "Americas | 12",
                "Total | 30", "Total | 12", "Total | 45", "Net sales | $ | 29,789"));
    }

    @Test
    void a_title_cut_after_a_connecting_word_takes_the_next_line() {
        String text = String.join("\n",
                "PART I", "ITEM 2. MANAGEMENT'S DISCUSSION AND ANALYSIS OF FINANCIAL CONDITION AND",
                "RESULTS OF OPERATIONS", "Due to rounding, numbers may not add up.",
                "ITEM 3. MARKET RISK", "See the 10-K.");

        assertThat(extractor.extract(FilingType.TEN_Q, text)).containsExactly(new FilingSection("I-2",
                "MANAGEMENT'S DISCUSSION AND ANALYSIS OF FINANCIAL CONDITION AND RESULTS OF OPERATIONS",
                "Due to rounding, numbers may not add up."));
    }

    @Test
    void the_exhibit_loses_its_page_numbers() {
        assertThat(extractor.exhibit991("1\nEXHIBIT 99.1\n3Q 2026 Earnings Release\n2\nResults rose."))
                .contains(new FilingSection(SectionPolicy.EX_99_1, "Exhibit 99.1",
                        "EXHIBIT 99.1\n3Q 2026 Earnings Release\nResults rose."));
    }

    // --- real text: HtmlText output of held issuers' filings (EDGAR, 2026-11-15), cut to excerpts by line --------

    private static String excerpt(String name) throws IOException {
        try (InputStream in = SectionExtractorTest.class.getResourceAsStream("/research/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertNoPageFurniture(List<FilingSection> sections) {
        assertThat(sections).allSatisfy(section -> assertThat(section.text().lines())
                .noneMatch(line -> line.matches("\\d{1,3}"))
                .noneMatch(line -> line.matches("(?i)(PART [IV]+|ITEM \\d+[A-Z]?)"))
                .noneMatch(line -> line.toLowerCase(Locale.ROOT).contains("table of contents"))
                .noneMatch(line -> line.matches(".*Form 10-[KQ] \\| \\d{1,3}")));
    }

    @Test
    void msft_ten_k_with_running_headers_on_every_page() throws IOException {
        List<FilingSection> sections = extractor.extract(FilingType.TEN_K, excerpt("msft-10k-excerpt.txt"));

        assertThat(sections).extracting(FilingSection::itemId).containsExactly("1", "1A", "7", "7A");
        assertThat(sections).extracting(FilingSection::title).containsExactly("BUSINESS", "RISK FACTORS",
                "MANAGEMENT’S DISCUSSION AND ANALYSIS OF FINANCIAL CONDITION AND RESULTS OF OPERATIONS",
                "QUANTITATIVE AND QUALITATIVE DISCLOSURES ABOUT MARKET RISK");
        assertThat(sections.getFirst().text()).startsWith("GENERAL");
        assertNoPageFurniture(sections);
    }

    @Test
    void msft_ten_q_running_part_headers_keep_the_part_keys() throws IOException {
        List<FilingSection> sections = extractor.extract(FilingType.TEN_Q, excerpt("msft-10q-excerpt.txt"));

        assertThat(sections).extracting(FilingSection::itemId).containsExactly("I-2", "II-1A");
        assertThat(sections).extracting(FilingSection::title).containsExactly(
                "MANAGEMENT’S DISCUSSION AND ANALYSIS OF FINANCIAL CONDITION AND RESULTS OF OPERATIONS",
                "RISK FACTORS");
        assertNoPageFurniture(sections);
    }

    @Test
    void jpm_ten_k_footers_are_dropped_and_item_7_is_the_cross_reference() throws IOException {
        List<FilingSection> sections = extractor.extract(FilingType.TEN_K, excerpt("jpm-10k-excerpt.txt"));

        assertThat(sections).extracting(FilingSection::itemId).containsExactly("1", "1A", "7", "7A");
        assertThat(sections.get(1).title()).isEqualTo("Risk Factors.");
        // JPM's MD&A sits in the financial section, outside any Item heading: Item 7 is only a pointer (accepted gap).
        assertThat(sections.get(2).text()).startsWith("Management’s discussion and analysis").hasSizeLessThan(500);
        assertThat(sections).allSatisfy(s -> assertThat(s.text()).doesNotContain("JPMorgan Chase & Co./2025 Form 10-K"));
        assertNoPageFurniture(sections);
    }

    @Test
    void xom_ten_q_split_title_and_contents_rows() throws IOException {
        List<FilingSection> sections = extractor.extract(FilingType.TEN_Q, excerpt("xom-10q-excerpt.txt"));

        assertThat(sections).extracting(FilingSection::itemId).startsWith("I-2");
        assertThat(sections.getFirst().title())
                .isEqualTo("MANAGEMENT'S DISCUSSION AND ANALYSIS OF FINANCIAL CONDITION AND RESULTS OF OPERATIONS");
        assertNoPageFurniture(sections);
    }
}
