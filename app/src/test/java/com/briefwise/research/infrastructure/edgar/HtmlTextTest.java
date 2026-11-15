package com.briefwise.research.infrastructure.edgar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class HtmlTextTest {

    private static String text(String html) {
        return HtmlText.of(Jsoup.parse(html));
    }

    @Test
    void one_line_per_block_and_inline_elements_joined() {
        String html = """
                <body><div><span>Item 1A.</span><span>  Risk&#160;Factors</span></div>
                <p>The Company’s   business
                   can be affected.</p><div><span><br/></span></div><div>Next<br>line</div></body>""";

        assertThat(text(html)).isEqualTo("Item 1A. Risk Factors\nThe Company’s business can be affected.\nNext\nline");
    }

    @Test
    void every_unicode_space_becomes_a_plain_space() {   // seen 2026-11-15 in filing-agent 8-Ks: Item&#8201;8.01
        String html = "<body><p>Item&#8201;8.01.&#8194;Other&#8239;Events</p><p>a&#160;&#160;b</p></body>";

        assertThat(text(html)).isEqualTo("Item 8.01. Other Events\na b");
    }

    @Test
    void table_rows_become_cell_rows_without_empty_cells() {
        String html = """
                <body><p>Before</p><table>
                <tr><td></td><td>Three Months Ended</td><td>&#160;</td></tr>
                <tr><td>Net sales</td><td>$</td><td>114,500</td></tr>
                <tr><td colspan="3"></td></tr>
                </table><p>After</p></body>""";

        assertThat(text(html)).isEqualTo("Before\nThree Months Ended\nNet sales | $ | 114,500\nAfter");
    }

    @Test
    void nested_table_stays_inside_its_cell() {
        String html = "<body><table><tr><td>Outer</td><td><table><tr><td>a</td><td>b</td></tr></table></td></tr>"
                + "</table></body>";

        assertThat(text(html)).isEqualTo("Outer | a b");
    }

    @Test
    void hidden_xbrl_header_scripts_and_styles_are_dropped() {
        String html = """
                <html><head><style>p { color: red }</style></head><body>
                <div style="display:none"><ix:header><ix:hidden>
                <ix:nonNumeric name="dei:AmendmentFlag">false</ix:nonNumeric></ix:hidden></ix:header></div>
                <div style="DISPLAY: NONE">hidden too</div>
                <script>var x = 1;</script>
                <div>Revenue was <ix:nonFraction name="us-gaap:Revenues">114.5</ix:nonFraction> billion.</div>
                </body></html>""";

        assertThat(text(html)).isEqualTo("Revenue was 114.5 billion.");
    }

    @Test
    void sgml_wrapper_of_an_archive_exhibit_is_skipped() {
        String raw = """
                <DOCUMENT>
                <TYPE>EX-99.1
                <SEQUENCE>2
                <FILENAME>ex991.htm
                <DESCRIPTION>EX-99.1
                <TEXT>
                <html><body><div>Exhibit 99.1</div><div>Apple reports results</div></body></html>
                </TEXT>
                </DOCUMENT>
                """;

        String text = HtmlText.of(HtmlText.parse(raw.getBytes(StandardCharsets.UTF_8), "https://www.sec.gov/x"));

        assertThat(text).isEqualTo("Exhibit 99.1\nApple reports results");
    }

    @Test
    void plain_document_is_parsed_from_the_start() {
        String raw = "<?xml version='1.0' encoding='ASCII'?><html><body><p>Form 10-K</p></body></html>";

        assertThat(HtmlText.of(HtmlText.parse(raw.getBytes(StandardCharsets.US_ASCII), "https://www.sec.gov/x")))
                .isEqualTo("Form 10-K");
    }
}
