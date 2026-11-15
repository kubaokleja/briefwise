package com.briefwise.research.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts the plain text of a filing (one block element per line, tables as {@code cell | cell} rows) into the items
 * of {@link SectionPolicy}. Pure and deterministic.
 *
 * <p>A heading is a short line starting with {@code ITEM n} (or {@code PART n} as a boundary); a section runs to
 * the next heading. Filings name every item twice, first in the table of contents, then in the body: of all
 * occurrences of an item, the one with the longest body wins, which drops the table of contents (ADR 0025).
 * Bump {@link #VERSION} whenever the output can change: stored filings then re-ingest (ADR 0026).
 */
public final class SectionExtractor {

    public static final String VERSION = "1";

    /** Longer lines are prose that happens to start with "Item 7 ...", not headings. */
    private static final int MAX_HEADING_LENGTH = 200;
    private static final int MAX_TITLE_LINE_LENGTH = 150;

    private static final Pattern ITEM = Pattern.compile(
            "^ITEM\\s+(\\d{1,2}(?:\\.\\d{2})?[A-Z]?)\\b\\s*[.:\\-–—]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PART = Pattern.compile(
            "^PART\\s+(IV|I{1,3})\\b\\s*[.:\\-–—]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    private record Heading(int line, String key, String title) {}

    public List<FilingSection> extract(FilingType type, String text) {
        List<String> lines = lines(text);
        List<Heading> headings = headings(type, lines);
        List<FilingSection> sections = new ArrayList<>();
        for (String wanted : SectionPolicy.items(type)) {
            best(wanted, headings, lines).ifPresent(sections::add);
        }
        return List.copyOf(sections);
    }

    /** The whole exhibit as one EX-99.1 section; empty if it has no text. */
    public Optional<FilingSection> exhibit991(String text) {
        String body = normalise(lines(text));
        return body.isBlank()
                ? Optional.empty()
                : Optional.of(new FilingSection(SectionPolicy.EX_99_1, "Exhibit 99.1", body));
    }

    private static List<Heading> headings(FilingType type, List<String> lines) {
        List<Heading> headings = new ArrayList<>();
        String part = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty() || line.length() > MAX_HEADING_LENGTH) {
                continue;
            }
            Matcher partMatch = PART.matcher(line);
            if (partMatch.matches()) {
                part = partMatch.group(1).toUpperCase(Locale.ROOT);
                headings.add(new Heading(i, "PART-" + part, ""));
                continue;
            }
            Matcher itemMatch = ITEM.matcher(line);
            if (itemMatch.matches()) {
                String item = itemMatch.group(1).toUpperCase(Locale.ROOT);
                String key = type == FilingType.TEN_Q ? (part == null ? "?-" + item : part + "-" + item) : item;
                headings.add(new Heading(i, key, cleanTitle(itemMatch.group(2))));
            }
        }
        return headings;
    }

    private static Optional<FilingSection> best(String wanted, List<Heading> headings, List<String> lines) {
        FilingSection best = null;
        for (int h = 0; h < headings.size(); h++) {
            Heading heading = headings.get(h);
            if (!heading.key().equals(wanted)) {
                continue;
            }
            int end = h + 1 < headings.size() ? headings.get(h + 1).line() : lines.size();
            List<String> body = new ArrayList<>(lines.subList(heading.line() + 1, end));
            String title = heading.title();
            if (title.isEmpty()) {
                title = takeTitleLine(body);
            }
            String text = normalise(body);
            if (!text.isBlank() && (best == null || text.length() > best.text().length())) {
                best = new FilingSection(wanted, title, text);
            }
        }
        return Optional.ofNullable(best);
    }

    /** A heading split over two lines ("Item 1A." then "Risk Factors"): the first short body line is the title. */
    private static String takeTitleLine(List<String> body) {
        for (int i = 0; i < body.size(); i++) {
            String line = body.get(i);
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() <= MAX_TITLE_LINE_LENGTH) {
                body.remove(i);
                return cleanTitle(line);
            }
            return "";
        }
        return "";
    }

    private static String cleanTitle(String raw) {
        return raw.replace('|', ' ').replaceAll("\\s+", " ").strip();
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.replace(' ', ' ').split("\\R", -1)) {
            lines.add(line.strip());
        }
        return lines;
    }

    /** Lines joined by newlines, runs of blank lines collapsed to one, trimmed. */
    private static String normalise(List<String> lines) {
        StringBuilder out = new StringBuilder();
        boolean blank = false;
        for (String line : lines) {
            if (line.isEmpty()) {
                blank = !out.isEmpty();
                continue;
            }
            if (blank) {
                out.append('\n');
                blank = false;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }
}
