package com.todo.user.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Inactive CSRF-token contract for future cookie-based refresh/logout requests.
 *
 * A CSRF token is an HMAC over a domain-separated fingerprint of the opaque
 * refresh token. It does not disclose the refresh token or HMAC key. After a
 * successful refresh rotation, the successor gets a different CSRF token.
 *
 * This class is not installed in the web security chain or any controller.
 * Future HTTP integration must pair this with strict Origin checks, a
 * browser-readable CSRF delivery mechanism and authenticated cookies.
 */
public final class RefreshCsrfTokenPolicy {
    public static final String HEADER_NAME = "X-CSRF-Token";
    private static final byte[] CONTEXT =
            "todo-refresh-csrf:v1:".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern OPAQUE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CSRF_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecretKeySpec key;

    /**
     * Requires an independent 256-bit (or stronger) secret.
     * Do not reuse JWT_SECRET or store this key in the repository.
     */
    public RefreshCsrfTokenPolicy(byte[] csrfSecret) {
        Objects.requireNonNull(csrfSecret, "csrfSecret");
        if (csrfSecret.length < 32) {
            throw new IllegalArgumentException("CSRF secret must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(csrfSecret.clone(), "HmacSHA256");
    }

    /** Derives the browser-readable CSRF value from a valid opaque refresh token. */
    public String issue(String refreshToken) {
        if (!validRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid opaque refresh token");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sign(refreshToken));
    }

    /**
     * Fails closed on missing or malformed inputs; compares MACs in constant time.
     * Never compare the CSRF token to a raw refresh token.
     */
    public boolean permits(String refreshToken, String suppliedCsrfToken) {
        if (!validRefreshToken(refreshToken)
                || suppliedCsrfToken == null
                || !CSRF_TOKEN.matcher(suppliedCsrfToken).matches()) {
            return false;
        }
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(suppliedCsrfToken);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (supplied.length != 32) {
            return false;
        }
        return MessageDigest.isEqual(sign(refreshToken), supplied);
    }

    private static boolean validRefreshToken(String token) {
        if (token == null || !OPAQUE_TOKEN.matcher(token).matches()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            return decoded.length == 32
                    && token.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] sign(String refreshToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(CONTEXT);
            // SHA-256 fingerprint is already used by the refresh-session store;
            // the raw cookie secret is never included in the generated CSRF token.
            return mac.doFinal(RefreshTokenMaterial.fingerprint(refreshToken)
                    .getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }
}
