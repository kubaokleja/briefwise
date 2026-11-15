package com.briefwise.research.infrastructure.edgar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Filing HTML to the plain text {@link com.briefwise.research.domain.model.SectionExtractor} expects (ADR 0025): one
 * block element per line, each table row as {@code cell | cell} (empty cells dropped), inline elements joined.
 * Dropped: {@code script}, {@code style}, the inline-XBRL {@code ix:header} and anything hidden with
 * {@code display:none} (XBRL facts, not reader text). Bump {@link #VERSION} whenever the output can change.
 */
final class HtmlText {

    static final String VERSION = "2";   // 2: every Unicode space separator is a plain space (thin space in headings)

    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Zs}]+");

    private static final Set<String> BLOCKS = Set.of("address", "article", "blockquote", "body", "center", "dd", "div",
            "dl", "dt", "footer", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "ol", "p", "pre",
            "section", "ul");

    /** Archive exhibits come wrapped as {@code <DOCUMENT><TYPE>EX-99.1 ... <TEXT><html>...}: that header is not text. */
    private static final Pattern SGML_WRAPPER = Pattern.compile("^\\s*<DOCUMENT>.*?(?=<html)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int WRAPPER_SCAN_BYTES = 4096;

    private HtmlText() {
    }

    /** Parses a fetched document; the charset comes from its {@code <meta>} or BOM (jsoup), UTF-8 otherwise. */
    static Document parse(byte[] body, String baseUri) {
        int start = wrapperLength(body);
        try {
            return Jsoup.parse(new ByteArrayInputStream(body, start, body.length - start), null, baseUri);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bytes before {@code <html} if the document starts with the SGML wrapper; it is ASCII, so ISO-8859-1 is safe. */
    private static int wrapperLength(byte[] body) {
        String head = new String(body, 0, Math.min(body.length, WRAPPER_SCAN_BYTES), StandardCharsets.ISO_8859_1);
        Matcher matcher = SGML_WRAPPER.matcher(head);
        return matcher.find() ? matcher.end() : 0;
    }

    static String of(Document document) {
        Document copy = document.clone();
        copy.select("script, style, ix|header, [style~=(?i)display\\s*:\\s*none]").remove();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        walk(copy.body(), lines, line);
        flush(lines, line);
        return String.join("\n", lines);
    }

    private static void walk(Node node, List<String> lines, StringBuilder line) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode text) {
                line.append(text.getWholeText());
            } else if (child instanceof Element element) {
                String name = element.normalName();
                if (name.equals("br")) {
                    flush(lines, line);
                } else if (name.equals("table")) {
                    flush(lines, line);
                    table(element, lines);
                } else if (BLOCKS.contains(name)) {
                    flush(lines, line);
                    walk(element, lines, line);
                    flush(lines, line);
                } else {
                    walk(element, lines, line);
                }
            }
        }
    }

    /** Rows of this table only; a nested table's text stays inside its cell. */
    private static void table(Element table, List<String> lines) {
        for (Element row : table.select("tr")) {
            if (row.closest("table") != table) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (Element cell : row.children()) {
                if (cell.nameIs("td") || cell.nameIs("th")) {
                    String text = clean(cell.text());
                    if (!text.isEmpty()) {
                        cells.add(text);
                    }
                }
            }
            if (!cells.isEmpty()) {
                lines.add(String.join(" | ", cells));
            }
        }
    }

    private static void flush(List<String> lines, StringBuilder line) {
        String text = clean(line.toString());
        line.setLength(0);
        if (!text.isEmpty()) {
            lines.add(text);
        }
    }

    /**
     * Every Unicode space separator ({@code \p{Zs}}: no-break, thin, en, em ...) becomes one plain space. Filing agents
     * write {@code Item&#8201;8.01}, and the heading rules only know the plain space.
     */
    private static String clean(String raw) {
        return SPACES.matcher(raw).replaceAll(" ").strip();
    }
}
