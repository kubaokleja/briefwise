package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.shared.domain.model.Ticker;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

/**
 * The classification prompt from {@code prompts/news-classification/} (repo root, on the classpath via the pom).
 * {@code system.md} is the stable prefix (instructions + few-shot examples, cached, ADR 0012); {@code user.md} is the
 * per-item data block. {@code prompt.properties} holds the version and a SHA-256 of both files, so a test fails when
 * the prompt changes without a version bump.
 */
record NewsClassificationPrompt(String version, String recordedSha256, String system, String userTemplate,
                                int maxSummaryChars) {

    static final String DIRECTORY = "prompts/news-classification/";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(tickers|source|headline|summary)}");

    NewsClassificationPrompt {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(recordedSha256, "recordedSha256");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(userTemplate, "userTemplate");
    }

    static NewsClassificationPrompt fromClasspath(int maxSummaryChars) {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource(DIRECTORY + "prompt.properties").getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DIRECTORY + "prompt.properties", e);
        }
        return new NewsClassificationPrompt(properties.getProperty("version"), properties.getProperty("sha256"),
                read("system.md"), read("user.md"), maxSummaryChars);
    }

    /**
     * The data block for one item. Placeholders are replaced in a single pass, so a headline that contains
     * {@code {summary}} stays literal text. Screening (ADR 0013) has already rejected items with prompt tags.
     */
    String render(NewsItem item) {
        String tickers = item.tickers().stream().map(Ticker::symbol).sorted().collect(Collectors.joining(", "));
        String summary = item.summary().length() > maxSummaryChars
                ? item.summary().substring(0, maxSummaryChars) + "..."
                : item.summary();
        Matcher m = PLACEHOLDER.matcher(userTemplate);
        return m.replaceAll(match -> Matcher.quoteReplacement(switch (match.group(1)) {
            case "tickers" -> tickers;
            case "source" -> item.source();
            case "headline" -> item.headline();
            default -> summary;
        }));
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
