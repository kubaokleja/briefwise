package com.briefwise.news.infrastructure.eval;

import com.briefwise.news.domain.model.EvalLabels;
import com.briefwise.news.domain.model.EventType;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.model.Relevance;
import com.briefwise.news.domain.model.Sentiment;
import com.briefwise.news.domain.port.GoldenSet;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Reads {@code evals/sentiment_golden.csv} as written by {@code evals/labelling/build_golden.py}: UTF-8, a header
 * row, RFC 4180 quoting (fields may hold commas, quotes and line breaks). Tickers are space-separated. The file is
 * the decrypted copy of the committed {@code .gpg} (ADR 0018); a missing file says how to get it.
 */
@Component
class CsvGoldenSet implements GoldenSet {

    static final List<String> COLUMNS = List.of("id", "sample", "tickers", "source", "headline", "summary",
            "sentiment", "relevance", "event_type");

    private final Path path;

    CsvGoldenSet(NewsEvalProperties properties) {
        this.path = properties.goldenPath();
    }

    @Override
    public List<GoldenCase> load() {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("No golden set at " + path.toAbsolutePath()
                    + "; decrypt it first: bash evals/scripts/decrypt-golden.sh (needs EVAL_DATA_PASSPHRASE)");
        }
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the golden set at " + path, e);
        }
        List<List<String>> rows = parse(content.startsWith("﻿") ? content.substring(1) : content);
        if (rows.isEmpty()) {
            throw new IllegalStateException("The golden set at " + path + " has no header");
        }
        Map<String, Integer> header = indexOf(rows.getFirst());
        List<GoldenCase> cases = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() == 1 && row.getFirst().isBlank()) {
                continue;
            }
            try {
                cases.add(toCase(row, header));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Golden set row " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return cases;
    }

    private static Map<String, Integer> indexOf(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(header.get(i).strip(), i);
        }
        List<String> missing = COLUMNS.stream().filter(c -> !index.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Golden set columns missing: " + missing);
        }
        return index;
    }

    private static GoldenCase toCase(List<String> row, Map<String, Integer> header) {
        if (row.size() < header.size()) {
            throw new IllegalArgumentException("expected " + header.size() + " fields, found " + row.size());
        }
        Set<Ticker> tickers = Arrays.stream(field(row, header, "tickers").split("\\s+"))
                .filter(s -> !s.isBlank()).map(Ticker::new).collect(Collectors.toSet());
        return new GoldenCase(UUID.fromString(field(row, header, "id")),
                "random".equals(field(row, header, "sample")), tickers, field(row, header, "source"),
                field(row, header, "headline"), field(row, header, "summary"),
                new EvalLabels(Sentiment.valueOf(field(row, header, "sentiment")),
                        Relevance.valueOf(field(row, header, "relevance")),
                        EventType.valueOf(field(row, header, "event_type"))));
    }

    private static String field(List<String> row, Map<String, Integer> header, String column) {
        return row.get(header.get(column)).strip();
    }

    /** RFC 4180: quoted fields may contain commas, CR/LF and doubled quotes; records end at CRLF or LF. */
    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (quoted) {
            throw new IllegalStateException("Unterminated quoted field in the golden set");
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
