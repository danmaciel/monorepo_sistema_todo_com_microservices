package com.todo.user.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates opaque refresh token material and its SHA-256 fingerprint.
 *
 * This utility does not persist sessions, rotate tokens, or expose an HTTP endpoint.
 * Callers must persist only the fingerprint and handle one-time rotation atomically.
 */
public final class RefreshTokenMaterial {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenMaterial() {
    }

    public static String generate() {
        byte[] token = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public static String fingerprint(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token must not be empty");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static boolean matches(String rawToken, String storedFingerprint) {
        if (rawToken == null || rawToken.isBlank()
                || storedFingerprint == null || storedFingerprint.isBlank()) {
            return false;
        }
        String computed = fingerprint(rawToken);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.US_ASCII),
                storedFingerprint.getBytes(StandardCharsets.US_ASCII));
    }
}
