package com.briefwise.compliance.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pattern list for indirect prompt injection in untrusted text (M1 scope, ADR 0013). Catches known phrasings only:
 * instruction overrides, chat role markers and tags that could close or open a prompt's data block. Paraphrases
 * pass; that is accepted while the model has no tools and answers in a fixed schema.
 */
public final class InjectionRules {

    /** One named rule; the name is what a screening result reports. */
    public record Rule(String name, Pattern pattern) {

        public Rule {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE;

    public static final List<Rule> M1 = List.of(
            new Rule("instruction-override", Pattern.compile(
                    "\\b(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+|your\\s+)?"
                            + "(previous|prior|above|earlier|preceding|system)\\s+"
                            + "(instructions?|prompts?|rules|messages|directions)", FLAGS)),
            // Not "you are now a ..." or "system prompt": clickbait ("you are now a millionaire") and AI-industry
            // news use them, and a false positive costs a classification.
            new Rule("new-instructions", Pattern.compile("\\b(new|updated|real)\\s+instructions\\s*:", FLAGS)),
            new Rule("role-marker", Pattern.compile(
                    "^\\s*(system|assistant|user|human)\\s*:|<\\|im_(start|end)\\|>|\\[/?INST]", FLAGS)),
            new Rule("prompt-tag", Pattern.compile(
                    "</?\\s*(news_item|data|document|system|instructions?|user|assistant)\\b[^>]*>", FLAGS)));

    private InjectionRules() {
    }

    /** Names of the rules that match {@code text}, in rule order; empty if none. */
    public static List<String> matching(List<Rule> rules, String text) {
        Objects.requireNonNull(text, "text");
        return rules.stream().filter(r -> r.pattern().matcher(text).find()).map(Rule::name).toList();
    }
}
