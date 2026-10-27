package com.briefwise.aiops.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * The lookup key of a recorded answer (L7, ADR 0015): SHA-256 over feature, model, prompt version and the normalised
 * variable input (the data block, not the system prompt; the prompt version stands for that). Normalisation is light
 * on purpose: Unicode NFC, line endings, surrounding and repeated whitespace. Case and punctuation are kept, because
 * "Apple cuts guidance" and "apple cuts guidance?" may deserve different answers. Call options (effort, max tokens,
 * schema) are not part of the key.
 */
public record ReplayKey(String hash) {

    /** Advisor context entry that carries the key of a call ({@code ChatClient} advisor params). */
    public static final String CONTEXT_PARAM = "briefwise.replay.key";

    /** Advisor context entry that carries the feature, for log lines and the miss exception. */
    public static final String FEATURE_PARAM = "briefwise.replay.feature";

    /** Response metadata entry set on a replayed answer, so the caller does not log or bill it. */
    public static final String REPLAYED_METADATA = "briefwise.replayed";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Between the key parts, so ("ab", "c") and ("a", "bc") never collide. */
    private static final char SEPARATOR = '\u001F';

    public ReplayKey {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("replay key must be 64 lowercase hex characters: " + hash);
        }
    }

    public static ReplayKey of(String feature, String model, String promptVersion, String input) {
        requireText(feature, "feature");
        requireText(model, "model");
        requireText(promptVersion, "promptVersion");
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        String material = feature + SEPARATOR + model + SEPARATOR + promptVersion + SEPARATOR + normalize(input);
        return new ReplayKey(sha256(material));
    }

    /** NFC, CRLF/CR to LF, then every whitespace run (incl. newlines) to one space, trimmed. */
    static String normalize(String input) {
        String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
        String lf = nfc.replace("\r\n", "\n").replace('\r', '\n');
        return WHITESPACE.matcher(lf).replaceAll(" ").strip();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return hash;
    }
}
