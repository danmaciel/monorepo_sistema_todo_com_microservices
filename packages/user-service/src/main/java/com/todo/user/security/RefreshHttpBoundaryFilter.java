package com.todo.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Fail-closed HTTP boundary for future refresh/logout routes.
 *
 * Session login, refresh and logout handlers are gated off by default, even
 * if someone later introduces a matching controller without enabling the
 * session transport explicitly.
 */
@Component
public class RefreshHttpBoundaryFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final RefreshRequestOriginPolicy origins;
    private final RefreshCsrfTokenPolicy csrf;

    public RefreshHttpBoundaryFilter(
            @Value("${auth.refresh.http-enabled:false}") boolean enabled,
            @Value("${auth.refresh.allowed-origins:}") String allowedOrigins,
            @Value("${auth.refresh.csrf-key-base64:}") String csrfKeyBase64) {
        this.enabled = enabled;
        if (enabled) {
            if (allowedOrigins == null || allowedOrigins.isBlank()
                    || csrfKeyBase64 == null || csrfKeyBase64.isBlank()) {
                throw new IllegalArgumentException(
                        "Refresh HTTP requires allowed origins and an independent CSRF key");
            }
            byte[] secret;
            try {
                secret = Base64.getDecoder().decode(csrfKeyBase64);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid refresh CSRF key encoding", ex);
            }
            this.origins = new RefreshRequestOriginPolicy(
                    Arrays.stream(allowedOrigins.split(",", -1))
                            .map(String::trim).toList());
            this.csrf = new RefreshCsrfTokenPolicy(secret);
            Arrays.fill(secret, (byte) 0);
        } else {
            this.origins = null;
            this.csrf = null;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !("/users/session/login".equals(path) || "/users/session/csrf".equals(path)
                || "/users/refresh".equals(path) || "/users/logout".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // CORS preflight has no refresh credentials and is handled by CORS.
        // It does not perform a session operation.
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!enabled) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if ("/users/session/csrf".equals(request.getServletPath())) {
            // A safe GET is allowed to retrieve only the CSRF proof for an
            // existing active cookie; the controller verifies DB eligibility.
            if (!"GET".equals(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            if (!origins.permits(request) || singleRefreshCookie(request) == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (!"POST".equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if ("/users/session/login".equals(request.getServletPath())) {
            if (!origins.permits(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        String rawToken = singleRefreshCookie(request);
        String csrfHeader = singleCsrfHeader(request);
        if (!origins.permits(request)
                || rawToken == null
                || csrfHeader == null
                || !csrf.permits(rawToken, csrfHeader)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Used only by the opt-in session handlers to issue a response CSRF token. */
    public String issueCsrf(String rawRefreshToken) {
        if (!enabled) {
            throw new IllegalStateException("Refresh HTTP is disabled");
        }
        return csrf.issue(rawRefreshToken);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static String singleRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String value = null;
        for (Cookie cookie : cookies) {
            if (RefreshCookiePolicy.COOKIE_NAME.equals(cookie.getName())) {
                if (value != null || cookie.getValue() == null
                        || cookie.getValue().isBlank()) {
                    return null;
                }
                value = cookie.getValue();
            }
        }
        return value;
    }

    private static String singleCsrfHeader(HttpServletRequest request) {
        var headers = request.getHeaders(RefreshCsrfTokenPolicy.HEADER_NAME);
        if (headers == null || !headers.hasMoreElements()) {
            return null;
        }
        String header = headers.nextElement();
        return headers.hasMoreElements() ? null : header;
    }
}
