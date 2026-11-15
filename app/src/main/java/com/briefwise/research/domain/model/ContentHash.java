package com.briefwise.research.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * SHA-256 (lower-case hex) over the sections in order. Each field is length-prefixed, so moving text from one
 * section to the next changes the hash. Changing this form changes every stored hash: every filing re-ingests once.
 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String of(List<FilingSection> sections) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        for (FilingSection section : sections) {
            update(digest, section.itemId());
            update(digest, section.title());
            update(digest, section.text());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String field) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
        digest.update(bytes);
    }
}
