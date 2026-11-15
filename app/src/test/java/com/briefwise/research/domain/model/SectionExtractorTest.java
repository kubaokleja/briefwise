package com.briefwise.research.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Synthetic text shaped like {@code HtmlToText} output. Real trimmed filings are covered by the EDGAR adapter tests
 * (fixtures in {@code test/resources/edgar/}).
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
}
