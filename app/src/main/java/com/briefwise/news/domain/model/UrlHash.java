package com.briefwise.news.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * SHA-256 (hex) of a normalised article URL: the exact-duplicate key. Normalisation lower-cases scheme and host and
 * drops the fragment; path and query are kept as they are, because sources put the article ID there.
 */
public record UrlHash(String value) {

    private static final Pattern HEX_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public UrlHash {
        Objects.requireNonNull(value, "value");
        if (!HEX_SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a SHA-256 hex digest: '" + value + "'");
        }
    }

    /** @throws IllegalArgumentException if the URL is not an absolute http(s) URL */
    public static UrlHash of(String url) {
        return new UrlHash(sha256(normalise(url)));
    }

    static String normalise(String url) {
        Objects.requireNonNull(url, "url");
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL: '" + url + "'", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getRawAuthority() == null) {
            throw new IllegalArgumentException("Not an absolute http(s) URL: '" + url + "'");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return scheme + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT) + path + query;
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JDK", e);
        }
    }
}
