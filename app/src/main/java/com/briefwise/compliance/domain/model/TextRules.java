package com.briefwise.compliance.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic compliance rules for generated text (M1 scope, ADR 0017). The recommendation and forecast patterns
 * match advice phrasing in the text's own voice ("investors should consider selling", "the stock will rise"), not
 * the bare words: a summary must be able to report "Northgate downgraded MSFT to Sell" or "raised its price target
 * to $200". Paraphrases pass; the LLM evaluator in M4 and the RM's approval are the next layers.
 */
public final class TextRules {

    /** One named pattern of a rule. */
    public record Rule(RuleId id, String name, Pattern pattern) {

        public Rule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final String ACTION = "(buy|sell|hold|add|reduce|trim|exit|increase|accumulate|consider|switch)";
    private static final String MOVE = "(rise|fall|climb|drop|decline|gain|rebound|recover|rally|surge|soar|plunge"
            + "|outperform|underperform|increase|decrease)";

    public static final List<Rule> M1 = List.of(
            new Rule(RuleId.NO_RECOMMENDATION, "advice-to-act", Pattern.compile(
                    "\\b(you|clients?|investors?|holders?|shareholders?|we)\\s+(should|must|ought\\s+to|need\\s+to)"
                            + "\\s+" + ACTION + "\\b", FLAGS)),
            new Rule(RuleId.NO_RECOMMENDATION, "suggested-trade", Pattern.compile(
                    "\\b(consider|recommends?|recommended|advises?|advised|suggests?)\\s+(buying|selling|holding"
                            + "|adding|reducing|trimming|exiting|accumulating)\\b", FLAGS)),
            new Rule(RuleId.NO_RECOMMENDATION, "trade-instruction", Pattern.compile(
                    "\\b(buy|sell|hold|add\\s+to|reduce|trim|exit)\\s+(the|this|these|your)\\s+(stock|share"
                            + "|position|holding)s?\\b", FLAGS)),
            new Rule(RuleId.NO_RECOMMENDATION, "timing-advice", Pattern.compile(
                    "\\b(good|right|great)\\s+time\\s+to\\s+(buy|sell|invest|add)\\b"
                            + "|\\b(take|lock\\s+in)\\s+profits?\\b|\\b(our|my)\\s+recommendation\\b", FLAGS)),
            new Rule(RuleId.NO_FORECAST, "price-prediction", Pattern.compile(
                    "\\b(will|should)\\s+(likely\\s+)?" + MOVE + "\\b", FLAGS)),
            new Rule(RuleId.NO_FORECAST, "expected-move", Pattern.compile(
                    "\\b(likely|expected|poised|set|bound|certain|sure)\\s+to\\s+" + MOVE + "\\b", FLAGS)),
            new Rule(RuleId.NO_FORECAST, "performance-promise", Pattern.compile(
                    "\\b(guaranteed|assured|certain|risk-free)\\s+(returns?|gains?|profits?|income)\\b"
                            + "|\\b(further\\s+)?upside\\s+(of|potential)\\b", FLAGS)));

    private TextRules() {
    }

    /**
     * Checks every passage against {@code rules} and for at least one citation, and the full text for the
     * disclaimer. Violations come in passage order, then rule order.
     */
    public static ComplianceResult check(List<Rule> rules, List<Passage> passages, String fullText,
                                         String disclaimer) {
        Objects.requireNonNull(fullText, "fullText");
        List<Violation> violations = new ArrayList<>();
        for (Passage passage : passages) {
            for (Rule rule : rules) {
                Matcher m = rule.pattern().matcher(passage.text());
                if (m.find()) {
                    violations.add(new Violation(rule.id(), m.group(), explain(rule)));
                }
            }
            if (passage.citations() < 1) {
                violations.add(new Violation(RuleId.CITATION_REQUIRED, passage.text(),
                        "Every factual statement must cite at least one source."));
            }
        }
        if (!fullText.contains(disclaimer)) {
            violations.add(new Violation(RuleId.DISCLAIMER_REQUIRED, "", "The fixed disclaimer is missing."));
        }
        return new ComplianceResult(violations);
    }

    private static String explain(Rule rule) {
        return switch (rule.id()) {
            case NO_RECOMMENDATION -> "Recommends or suggests an investment action (" + rule.name() + ").";
            case NO_FORECAST -> "Predicts a price move or promises performance (" + rule.name() + ").";
            default -> rule.name();
        };
    }
}
