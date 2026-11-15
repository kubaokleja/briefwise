package com.briefwise.research.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts the plain text of a filing (one block element per line, tables as {@code cell | cell} rows) into the items
 * of {@link SectionPolicy}. Pure and deterministic.
 *
 * <p>A heading is a short line starting with {@code ITEM n} (or {@code PART n} as a boundary); a section runs to
 * the next heading. Filings name every item twice, first in the table of contents, then in the body: of all
 * occurrences of an item, the one with the longest body wins, which drops the table of contents (ADR 0025).
 *
 * <p>Page furniture is dropped first, as seen on the held issuers' filings (2026-11-15):
 * <ul>
 *   <li>running headers: a heading line repeated {@value #RUNNING_HEADER_MIN} or more times verbatim (MSFT prints
 *       {@code PART I} / {@code Item 1} on every page), and every line of the same shape ({@code Item 7A} on a
 *       one-page item). It is no boundary; a running {@code PART} still sets the part of the 10-Q items that
 *       follow;</li>
 *   <li>bare page numbers ({@code 12}, {@code - 12 -}, {@code Page 12}) and {@code Table of Contents} links;</li>
 *   <li>footers: the same text before a page number, {@value #RUNNING_HEADER_MIN} or more times with rising
 *       numbers ({@code Apple Inc. | 2026 Form 10-K | 12}). Rising numbers keep a repeated table row.</li>
 * </ul>
 * A title cut after a connecting word ({@code ... CONDITION AND}) takes the next short line.
 * Bump {@link #VERSION} whenever the output can change: stored filings then re-ingest (ADR 0026).
 */
public final class SectionExtractor {

    public static final String VERSION = "2";

    /** Longer lines are prose that happens to start with "Item 7 ...", not headings. */
    private static final int MAX_HEADING_LENGTH = 200;
    private static final int MAX_TITLE_LINE_LENGTH = 150;
    static final int RUNNING_HEADER_MIN = 3;

    private static final Pattern ITEM = Pattern.compile(
            "^ITEM\\s+(\\d{1,2}(?:\\.\\d{2})?[A-Z]?)\\b\\s*[.:\\-–—]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PART = Pattern.compile(
            "^PART\\s+(IV|I{1,3})\\b\\s*[.:\\-–—]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^(?:PAGE\\s+)?[-–—]?\\s*\\d{1,3}\\s*[-–—]?$", Pattern.CASE_INSENSITIVE);
    /** One cell of a back-link row: {@code Table of Contents}, {@code Back to Financial Table of Contents}. */
    private static final Pattern TOC_LINK = Pattern.compile(
            "^(?:(?:RETURN|BACK)\\s+TO\\s+)?(?:\\p{L}+\\s+)?TABLE\\s+OF\\s+CONTENTS$", Pattern.CASE_INSENSITIVE);
    /** Text with a letter, then a page number after {@code |} or a space ({@code 29,789} does not match). */
    private static final Pattern FOOTER = Pattern.compile("^(.*\\p{L}.*?)(?:\\s*\\|\\s*|\\s+)(\\d{1,3})$");
    private static final Pattern OPEN_TITLE = Pattern.compile(
            "(?:\\b(?:AND|OF|THE|FOR|IN|ON|TO)|[,–—-])$", Pattern.CASE_INSENSITIVE);

    private record Heading(int line, String key, String title) {}

    public List<FilingSection> extract(FilingType type, String text) {
        List<String> raw = lines(text);
        Set<String> running = runningHeaderShapes(raw);
        boolean[] furniture = furniture(raw, running);
        List<String> lines = new ArrayList<>();
        List<String> runningParts = new ArrayList<>(); // per kept line: the part a dropped running PART set, or null
        String pendingPart = null;
        for (int i = 0; i < raw.size(); i++) {
            String line = raw.get(i);
            if (furniture[i]) {
                Matcher part = PART.matcher(line);
                if (part.matches() && isRunningHeader(line, running)) {
                    pendingPart = part.group(1).toUpperCase(Locale.ROOT);
                }
                continue;
            }
            lines.add(line);
            runningParts.add(pendingPart);
            pendingPart = null;
        }
        List<Heading> headings = headings(type, lines, runningParts);
        List<FilingSection> sections = new ArrayList<>();
        for (String wanted : SectionPolicy.items(type)) {
            best(wanted, headings, lines).ifPresent(sections::add);
        }
        return List.copyOf(sections);
    }

    /** The whole exhibit as one EX-99.1 section; empty if it has no text. */
    public Optional<FilingSection> exhibit991(String text) {
        List<String> raw = lines(text);
        boolean[] furniture = furniture(raw, Set.of());
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            if (!furniture[i]) {
                kept.add(raw.get(i));
            }
        }
        String body = normalise(kept);
        return body.isBlank()
                ? Optional.empty()
                : Optional.of(new FilingSection(SectionPolicy.EX_99_1, "Exhibit 99.1", body));
    }

    /**
     * Shapes ({@link #shape}) of heading lines repeated verbatim often enough to be page headers. A shape, not the
     * line: a one-page item has its running header ({@code Item 7A}) only once, but in the same shape
     * ({@code Item #}) as the long items.
     */
    private static Set<String> runningHeaderShapes(List<String> lines) {
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines) {
            if (shape(line).isPresent()) {
                counts.merge(line, 1, Integer::sum);
            }
        }
        Set<String> shapes = new HashSet<>();
        counts.forEach((line, count) -> {
            if (count >= RUNNING_HEADER_MIN) {
                shape(line).ifPresent(shapes::add);
            }
        });
        return shapes;
    }

    private static boolean isRunningHeader(String line, Set<String> shapes) {
        return !shapes.isEmpty() && shape(line).filter(shapes::contains).isPresent();
    }

    /** A heading line with its item or part number replaced by {@code #}; empty for other lines. */
    private static Optional<String> shape(String line) {
        if (line.isEmpty() || line.length() > MAX_HEADING_LENGTH) {
            return Optional.empty();
        }
        for (Pattern pattern : List.of(ITEM, PART)) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return Optional.of(line.substring(0, matcher.start(1)) + "#" + line.substring(matcher.end(1)));
            }
        }
        return Optional.empty();
    }

    private static boolean[] furniture(List<String> lines, Set<String> running) {
        boolean[] drop = new boolean[lines.size()];
        Map<String, List<Integer>> footerCandidates = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            if (isRunningHeader(line, running) || PAGE_NUMBER.matcher(line).matches() || isTocLink(line)) {
                drop[i] = true;
                continue;
            }
            Matcher footer = FOOTER.matcher(line);
            if (line.length() <= MAX_HEADING_LENGTH && footer.matches()) {
                footerCandidates.computeIfAbsent(footer.group(1).strip(), k -> new ArrayList<>()).add(i);
            }
        }
        for (List<Integer> occurrences : footerCandidates.values()) {
            if (occurrences.size() >= RUNNING_HEADER_MIN && risingPageNumbers(lines, occurrences)) {
                occurrences.forEach(i -> drop[i] = true);
            }
        }
        return drop;
    }

    /** Every cell of the line is a table-of-contents link (XOM: {@code Table of Contents | Financial Table of ...}). */
    private static boolean isTocLink(String line) {
        for (String cell : line.split("\\|")) {
            if (!TOC_LINK.matcher(cell.strip()).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean risingPageNumbers(List<String> lines, List<Integer> occurrences) {
        int previous = -1;
        for (int i : occurrences) {
            Matcher footer = FOOTER.matcher(lines.get(i));
            if (!footer.matches()) {
                return false;
            }
            int page = Integer.parseInt(footer.group(2));
            if (page <= previous) {
                return false;
            }
            previous = page;
        }
        return true;
    }

    private static List<Heading> headings(FilingType type, List<String> lines, List<String> runningParts) {
        List<Heading> headings = new ArrayList<>();
        String part = null;
        for (int i = 0; i < lines.size(); i++) {
            if (runningParts.get(i) != null) {
                part = runningParts.get(i);
            }
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
            } else if (OPEN_TITLE.matcher(title).find()) {
                String rest = takeTitleLine(body);
                title = rest.isEmpty() ? title : title + " " + rest;
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
