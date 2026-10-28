package com.briefwise.compliance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextRulesTest {

    private static final String DISCLAIMER = "Fixed disclaimer.";

    private static ComplianceResult check(String passage) {
        return TextRules.check(TextRules.M1, List.of(new Passage(passage, 1)), passage + "\n" + DISCLAIMER,
                DISCLAIMER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Investors should consider selling Apple after the miss.",
            "Clients must reduce exposure: we recommend selling.",
            "The bank advises trimming the position.",
            "Consider adding to Microsoft on weakness.",
            "Buy the stock before earnings.",
            "Trim your positions in Nvidia.",
            "This could be a good time to buy.",
            "Holders may want to take profits.",
            "Our recommendation is unchanged."})
    void advice_phrasing_is_a_recommendation(String text) {
        assertThat(check(text).violations()).extracting(Violation::rule).contains(RuleId.NO_RECOMMENDATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Apple shares will rise after the beat.",
            "The stock should recover next quarter.",
            "Nvidia is likely to outperform the sector.",
            "Shares are poised to rally.",
            "This offers guaranteed returns.",
            "Analysts see further upside potential."})
    void price_predictions_and_promises_are_forecasts(String text) {
        assertThat(check(text).violations()).extracting(Violation::rule).contains(RuleId.NO_FORECAST);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Northgate downgraded Microsoft to Sell and cut its price target to $380.",
            "A broker upgraded Apple to Buy, citing iPhone demand.",
            "Morgan reiterated its Hold rating on Tesla.",
            "Apple is expected to report quarterly earnings on Thursday.",
            "Revenue rose 8% and the company raised its full-year guidance.",
            "The company will hold its annual meeting in March.",
            "Shares fell 4% after the regulator opened an investigation."})
    void reported_facts_and_analyst_actions_pass(String text) {
        assertThat(check(text).passed()).as(text).isTrue();
    }

    @Test
    void the_offending_phrase_is_reported_so_a_re_ask_can_name_it() {
        ComplianceResult result = check("Revenue beat estimates, so the shares will rise.");

        assertThat(result.violations()).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo(RuleId.NO_FORECAST);
            assertThat(v.offendingText()).isEqualTo("will rise");
        });
    }

    @Test
    void a_passage_without_a_citation_is_a_violation() {
        ComplianceResult result = TextRules.check(TextRules.M1, List.of(new Passage("Apple beat estimates.", 0)),
                "Apple beat estimates.\n" + DISCLAIMER, DISCLAIMER);

        assertThat(result.violations()).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo(RuleId.CITATION_REQUIRED);
            assertThat(v.offendingText()).isEqualTo("Apple beat estimates.");
        });
    }

    @Test
    void a_text_without_the_disclaimer_is_a_violation() {
        ComplianceResult result = TextRules.check(TextRules.M1, List.of(new Passage("Apple beat estimates.", 1)),
                "Apple beat estimates.", DISCLAIMER);

        assertThat(result.violations()).extracting(Violation::rule).containsExactly(RuleId.DISCLAIMER_REQUIRED);
    }

    @Test
    void the_fixed_disclaimer_itself_breaks_no_rule() {
        ComplianceResult result = TextRules.check(TextRules.M1, List.of(new Passage(Disclaimer.TEXT, 1)),
                Disclaimer.TEXT, Disclaimer.TEXT);

        assertThat(result.passed()).isTrue();
    }
}
