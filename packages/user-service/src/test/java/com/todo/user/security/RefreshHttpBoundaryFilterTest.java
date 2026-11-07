package com.todo.user.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class RefreshHttpBoundaryFilterTest {
    private static final String KEY = Base64.getEncoder().encodeToString(
            "separate-csrf-test-key-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
    private static final String ALLOWED_ORIGIN = "https://app.example.test";
    private static final String TOKEN = RefreshTokenMaterial.generate();
    private static final String CSRF = new RefreshCsrfTokenPolicy(
            Base64.getDecoder().decode(KEY)).issue(TOKEN);

    private RefreshHttpBoundaryFilter enabledFilter() {
        return new RefreshHttpBoundaryFilter(true, ALLOWED_ORIGIN, KEY);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private void signRequest(MockHttpServletRequest request) {
        request.addHeader("Origin", ALLOWED_ORIGIN);
        request.setCookies(new Cookie(RefreshCookiePolicy.COOKIE_NAME, TOKEN));
        request.addHeader(RefreshCsrfTokenPolicy.HEADER_NAME, CSRF);
    }

    private int status(RefreshHttpBoundaryFilter filter, MockHttpServletRequest request)
            throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void disabledByDefaultRejectsReservedPathsWithoutASecret() throws Exception {
        var filter = new RefreshHttpBoundaryFilter(false, "", "");
        assertEquals(404, status(filter, request("POST", "/users/refresh")));
        assertEquals(404, status(filter, request("POST", "/users/logout")));
    }

    @Test
    void enabledPathOnlyPassesValidOriginCookieAndCsrfTogether() throws Exception {
        var filter = enabledFilter();
        for (String path : new String[]{"/users/refresh", "/users/logout"}) {
            var valid = request("POST", path);
            signRequest(valid);
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(valid, response, chain);
            assertEquals(200, response.getStatus());
            assertNotNull(chain.getRequest(), "all three checks must succeed");

            var missingOrigin = request("POST", path);
            missingOrigin.setCookies(new Cookie(RefreshCookiePolicy.COOKIE_NAME, TOKEN));
            missingOrigin.addHeader(RefreshCsrfTokenPolicy.HEADER_NAME, CSRF);
            assertEquals(403, status(filter, missingOrigin));

            var wrongOrigin = request("POST", path);
            signRequest(wrongOrigin);
            wrongOrigin.removeHeader("Origin");
            wrongOrigin.addHeader("Origin", "https://evil.example.test");
            assertEquals(403, status(filter, wrongOrigin));

            var missingCookie = request("POST", path);
            missingCookie.addHeader("Origin", ALLOWED_ORIGIN);
            missingCookie.addHeader(RefreshCsrfTokenPolicy.HEADER_NAME, CSRF);
            assertEquals(403, status(filter, missingCookie));

            var missingCsrf = request("POST", path);
            missingCsrf.addHeader("Origin", ALLOWED_ORIGIN);
            missingCsrf.setCookies(new Cookie(RefreshCookiePolicy.COOKIE_NAME, TOKEN));
            assertEquals(403, status(filter, missingCsrf));

            var wrongCsrf = request("POST", path);
            signRequest(wrongCsrf);
            wrongCsrf.removeHeader(RefreshCsrfTokenPolicy.HEADER_NAME);
            wrongCsrf.addHeader(RefreshCsrfTokenPolicy.HEADER_NAME,
                    new RefreshCsrfTokenPolicy(Base64.getDecoder().decode(KEY))
                            .issue(RefreshTokenMaterial.generate()));
            assertEquals(403, status(filter, wrongCsrf));

            var duplicatedCookie = request("POST", path);
            signRequest(duplicatedCookie);
            duplicatedCookie.setCookies(
                    new Cookie(RefreshCookiePolicy.COOKIE_NAME, TOKEN),
                    new Cookie(RefreshCookiePolicy.COOKIE_NAME, TOKEN));
            assertEquals(403, status(filter, duplicatedCookie));

            var duplicatedHeader = request("POST", path);
            signRequest(duplicatedHeader);
            duplicatedHeader.addHeader(RefreshCsrfTokenPolicy.HEADER_NAME, CSRF);
            assertEquals(403, status(filter, duplicatedHeader));
        }
    }

    @Test
    void protectsAgainstUnintendedMethodsAndDoesNotBreakPreflights() throws Exception {
        var filter = enabledFilter();
        var get = request("GET", "/users/refresh");
        signRequest(get);
        assertEquals(405, status(filter, get));

        var options = request("OPTIONS", "/users/refresh");
        assertEquals(200, status(filter, options));
    }

    @Test
    void enablingRequiresExplicitIndependentKeyAndOrigins() {
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshHttpBoundaryFilter(true, "", KEY));
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshHttpBoundaryFilter(true, ALLOWED_ORIGIN, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshHttpBoundaryFilter(true, ALLOWED_ORIGIN, "not-base64!"));
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshHttpBoundaryFilter(true, ALLOWED_ORIGIN,
                        Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void skipsCurrentLoginAndApplicationRoutes() throws Exception {
        var filter = new RefreshHttpBoundaryFilter(false, "", "");
        for (String path : new String[]{"/users/login", "/users/register", "/users/me", "/actuator/health"}) {
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(request("POST", path), response, chain);
            assertNotNull(chain.getRequest(), "existing endpoints must not be intercepted");
        }
    }
}
