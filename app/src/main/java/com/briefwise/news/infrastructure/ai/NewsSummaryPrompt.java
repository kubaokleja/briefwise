package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.ClassifiedNews;
import com.briefwise.news.domain.model.RelevantNews;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.news.domain.model.SummaryViolation;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

/**
 * The summary prompt from {@code prompts/news-summary/} (ADR 0017). {@code system.md} holds the instructions and the
 * hard rules; {@code user.md} the data block: the selected items in importance order, each as a
 * {@code <news_item id="...">}, and, on a compliance re-ask, a {@code <corrections>} block. Only fields screened at
 * classification (headline, summary) and our own labels go in; no portfolio name, client or weight. The version and
 * a SHA-256 of both files are in {@code prompt.properties}, so a test fails when the prompt changes without a bump.
 */
record NewsSummaryPrompt(String version, String recordedSha256, String system, String userTemplate,
                         int maxSummaryChars) {

    static final String DIRECTORY = "prompts/news-summary/";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(items|corrections)}");

    NewsSummaryPrompt {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(recordedSha256, "recordedSha256");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(userTemplate, "userTemplate");
    }

    static NewsSummaryPrompt fromClasspath(int maxSummaryChars) {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource(DIRECTORY + "prompt.properties").getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DIRECTORY + "prompt.properties", e);
        }
        return new NewsSummaryPrompt(properties.getProperty("version"), properties.getProperty("sha256"),
                read("system.md"), read("user.md"), maxSummaryChars);
    }

    /**
     * The data block for one request. Deterministic for the same items in the same order, so the replay key is
     * stable. Placeholders are replaced in one pass, so item text containing {@code {corrections}} stays literal.
     */
    String render(SummaryRequest request) {
        String items = request.news().stream().map(this::item).collect(Collectors.joining());
        String corrections = request.corrections().isEmpty() ? "" : request.corrections().stream()
                .map(NewsSummaryPrompt::correction)
                .collect(Collectors.joining("", "<corrections>\n", "</corrections>\n"));
        Matcher m = PLACEHOLDER.matcher(userTemplate);
        return m.replaceAll(match -> Matcher.quoteReplacement(
                match.group(1).equals("items") ? items : corrections));
    }

    private String item(RelevantNews relevant) {
        ClassifiedNews n = relevant.news();
        String tickers = relevant.heldTickers().stream().map(Ticker::symbol).sorted()
                .collect(Collectors.joining(", "));
        String summary = n.summary().length() > maxSummaryChars
                ? n.summary().substring(0, maxSummaryChars) + "..."
                : n.summary();
        return "<news_item id=\"" + n.id() + "\">\n"
                + "tickers: " + tickers + "\n"
                + "published: " + LocalDate.ofInstant(n.publishedAt(), ZoneOffset.UTC) + "\n"
                + "relevance: " + n.relevance() + "\n"
                + "sentiment: " + n.sentiment() + "\n"
                + "event: " + n.eventType() + "\n"
                + "headline: " + n.headline() + "\n"
                + "summary: " + summary + "\n"
                + "</news_item>\n";
    }

    private static String correction(SummaryViolation v) {
        return "- " + v.rule() + (v.offendingText().isBlank() ? "" : ": \"" + v.offendingText() + "\"") + "\n";
    }

    /** SHA-256 over both prompt files, as recorded in {@code prompt.properties}. */
    String contentSha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(system.getBytes(StandardCharsets.UTF_8));
            digest.update(userTemplate.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(String file) {
        try (InputStream in = new ClassPathResource(DIRECTORY + file).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DIRECTORY + file, e);
        }
    }
}
