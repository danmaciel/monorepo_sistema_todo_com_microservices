package com.todo.user.security;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Strict Origin validation intended for future cookie-authenticated refresh/logout.
 *
 * Deliberately not installed as a filter yet: no refresh/logout HTTP routes exist,
 * and this check does not replace an independent CSRF token defense.
 */
public final class RefreshRequestOriginPolicy {
    private final Set<String> allowedOrigins;

    public RefreshRequestOriginPolicy(List<String> allowedOrigins) {
        Objects.requireNonNull(allowedOrigins, "allowedOrigins");
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed origin is required");
        }
        Set<String> validated = new HashSet<>();
        for (String origin : allowedOrigins) {
            validateConfiguredOrigin(origin);
            if (!validated.add(origin)) {
                throw new IllegalArgumentException("Duplicate allowed origin");
            }
        }
        this.allowedOrigins = Set.copyOf(validated);
    }

    /**
     * Fail closed: require precisely one Origin header with an exact configured
     * serialized origin. Never fall back to Referer or forwarded headers.
     */
    public boolean permits(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        Enumeration<String> values = request.getHeaders("Origin");
        if (values == null || !values.hasMoreElements()) {
            return false;
        }
        String origin = values.nextElement();
        if (values.hasMoreElements()) {
            return false;
        }
        return origin != null && allowedOrigins.contains(origin);
    }

    private static void validateConfiguredOrigin(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Origin must not be empty");
        }
        try {
            URI uri = new URI(value);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !value.equals(uri.toASCIIString())) {
                throw new IllegalArgumentException("Expected an exact HTTP(S) origin");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid allowed origin", ex);
        }
    }
}
