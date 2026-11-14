package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Ticker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Which golden set a run measured (ADR 0022): the number of cases and a SHA-256 over their content. Two runs are
 * comparable only on the same golden set; a different model or prompt version is what a run measures, a different
 * golden set changes the ruler itself.
 *
 * <p>The hash is over the parsed cases, not the file bytes, so line endings, a BOM or column order do not change it,
 * while any edit of a case (relabel, text fix, added or removed row) does. It is over the clear text, never the
 * {@code .gpg} copy, whose bytes differ on every encryption. The canonical form is a contract with every committed
 * {@code baseline.json}; changing it makes all baselines not comparable:
 * <ul>
 *   <li>cases sorted by {@code id.toString()};</li>
 *   <li>per case, separated by U+001F: id, {@code random} or {@code company}, tickers (symbols sorted, joined by a
 *       space), source, headline, summary, sentiment, relevance, event type (enum names);</li>
 *   <li>each case terminated by U+001E; UTF-8; lower-case hex.</li>
 * </ul>
 */
public record GoldenSetFingerprint(int cases, String sha256) {

    private static final char FIELD = '\u001F';
    private static final char RECORD = '\u001E';

    public GoldenSetFingerprint {
        if (cases < 0) {
            throw new IllegalArgumentException("cases must not be negative");
        }
        Objects.requireNonNull(sha256, "sha256");
    }

    public static GoldenSetFingerprint of(List<GoldenCase> cases) {
        StringBuilder canonical = new StringBuilder();
        cases.stream().sorted(Comparator.comparing(c -> c.id().toString())).forEach(c -> {
            EvalLabels labels = c.expected();
            String tickers = c.tickers().stream().map(Ticker::symbol).sorted().collect(Collectors.joining(" "));
            canonical.append(c.id()).append(FIELD)
                    .append(c.random() ? "random" : "company").append(FIELD)
                    .append(tickers).append(FIELD)
                    .append(c.source()).append(FIELD)
                    .append(c.headline()).append(FIELD)
                    .append(c.summary()).append(FIELD)
                    .append(labels.sentiment().name()).append(FIELD)
                    .append(labels.relevance().name()).append(FIELD)
                    .append(labels.eventType().name()).append(RECORD);
        });
        return new GoldenSetFingerprint(cases.size(), sha256(canonical.toString()));
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
