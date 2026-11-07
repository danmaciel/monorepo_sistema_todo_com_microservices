package com.todo.user.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Cookie serialization policy reserved for future refresh/logout HTTP endpoints.
 *
 * No current controller calls this class. HTTP transport, CSRF protection and
 * browser integration must be implemented and tested before activating it.
 */
public final class RefreshCookiePolicy {
    public static final String COOKIE_NAME = "__Secure-todo-refresh";
    public static final String COOKIE_PATH = "/api/users";

    private RefreshCookiePolicy() {
    }

    /**
     * Creates an HttpOnly refresh cookie for an HTTPS deployment.
     * Expires no later than the server-side absolute refresh session expiry.
     * The caller must pass a cryptographically generated opaque refresh token.
     */
    public static ResponseCookie issue(String rawToken, Instant expiresAt, Instant now) {
        Objects.requireNonNull(rawToken, "rawToken");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");
        if (rawToken.isBlank() || rawToken.indexOf('\r') >= 0 || rawToken.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid refresh token material");
        }
        Duration lifetime = Duration.between(now, expiresAt);
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Cannot issue an expired refresh session cookie");
        }
        // Max-Age has second precision: round down rather than outlive the DB session.
        long seconds = lifetime.getSeconds();
        if (seconds <= 0) {
            throw new IllegalArgumentException("Refresh session expires too soon");
        }
        return builder(rawToken).maxAge(Duration.ofSeconds(seconds)).build();
    }

    /** Clears only this refresh cookie with the exact same name and path. */
    public static ResponseCookie clear() {
        return builder("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH);
        // Deliberately host-only: never set Domain for this cookie.
    }
}
