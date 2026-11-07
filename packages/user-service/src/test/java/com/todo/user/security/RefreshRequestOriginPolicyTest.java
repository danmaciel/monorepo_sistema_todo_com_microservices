package com.todo.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RefreshRequestOriginPolicyTest {
    private final RefreshRequestOriginPolicy policy = new RefreshRequestOriginPolicy(
            List.of("http://localhost:3000", "http://localhost:5173", "https://app.example.test"));

    private MockHttpServletRequest request(String... origins) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users/refresh");
        for (String origin : origins) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    @Test
    void permitsExactlyConfiguredOrigins() {
        assertTrue(policy.permits(request("http://localhost:3000")));
        assertTrue(policy.permits(request("http://localhost:5173")));
        assertTrue(policy.permits(request("https://app.example.test")));
    }

    @Test
    void rejectsMissingNullAndMultipleOriginHeaders() {
        assertFalse(policy.permits(request()));
        assertFalse(policy.permits(request("null")));
        assertFalse(policy.permits(request("http://localhost:3000", "https://evil.example.test")));
        assertFalse(policy.permits(request("http://localhost:3000, https://evil.example.test")));
    }

    @Test
    void rejectsSimilarButDifferentOriginsAndRefererFallback() {
        assertFalse(policy.permits(request("http://localhost:5174")));
        assertFalse(policy.permits(request("https://app.example.test.evil.invalid")));
        assertFalse(policy.permits(request("http://app.example.test")));
        assertFalse(policy.permits(request("https://app.example.test:443")));
        assertFalse(policy.permits(request("https://app.example.test/")));
        assertFalse(policy.permits(request("https://APP.example.test")));

        var withReferer = request();
        withReferer.addHeader("Referer", "https://app.example.test/page");
        withReferer.addHeader("X-Forwarded-Host", "app.example.test");
        assertFalse(policy.permits(withReferer));
    }

    @Test
    void rejectsUnsafeConfiguredOriginValues() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshRequestOriginPolicy(List.of()));
        for (String invalid : List.of(
                "", "null", "*", "https://app.example.test/path",
                "https://app.example.test?x=1", "https://app.example.test/#frag",
                "https://user@app.example.test", "https://app.example.test,https://evil.invalid")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new RefreshRequestOriginPolicy(List.of(invalid)), invalid);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshRequestOriginPolicy(List.of("https://app.example.test", "https://app.example.test")));
    }
}
