package com.todo.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RefreshCookiePolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void issuedCookieHasSecurityFlagsHostOnlyScopeAndBoundedLifetime() {
        ResponseCookie cookie = RefreshCookiePolicy.issue(
                "opaque-test-token", NOW.plusSeconds(600), NOW);
        String header = cookie.toString();

        assertTrue(header.startsWith("__Secure-todo-refresh=opaque-test-token;"));
        assertTrue(header.contains("Path=/api/users"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        assertTrue(header.contains("Max-Age=600"));
        assertFalse(header.contains("Domain="), "cookie must remain host-only");
    }

    @Test
    void cookieLifetimeNeverRoundsBeyondDatabaseExpiry() {
        ResponseCookie cookie = RefreshCookiePolicy.issue(
                "opaque-test-token", NOW.plusSeconds(600).plusMillis(800), NOW);
        assertEquals(600, cookie.getMaxAge().getSeconds());
    }

    @Test
    void clearingCookieMatchesNamePathAndSecurityAttributes() {
        ResponseCookie clear = RefreshCookiePolicy.clear();
        String header = clear.toString();
        assertEquals("__Secure-todo-refresh", clear.getName());
        assertEquals("/api/users", clear.getPath());
        assertEquals(0L, clear.getMaxAge().getSeconds());
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        assertFalse(header.contains("Domain="));
    }

    @Test
    void refusesInvalidMaterialOrExpiredSession() {
        assertThrows(IllegalArgumentException.class,
                () -> RefreshCookiePolicy.issue("", NOW.plusSeconds(60), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> RefreshCookiePolicy.issue("bad\r\nvalue", NOW.plusSeconds(60), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> RefreshCookiePolicy.issue("token", NOW, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> RefreshCookiePolicy.issue("token", NOW.minusSeconds(1), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> RefreshCookiePolicy.issue("token", NOW.plusMillis(500), NOW));
    }
}
