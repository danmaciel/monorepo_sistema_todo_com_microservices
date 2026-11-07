package com.todo.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private final SecurityConfig config = new SecurityConfig(
            mock(JwtAuthenticationFilter.class), mock(UserDetailsService.class));

    @Test
    void allowedLocalOriginsDoNotEnableCredentialedCors() {
        for (String origin : new String[] {"http://localhost:3000", "http://localhost:5173"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/users/login");
            request.addHeader("Origin", origin);
            request.addHeader("Access-Control-Request-Method", "POST");

            CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);
            assertNotNull(cors);
            assertEquals(origin, cors.checkOrigin(origin));
            assertEquals(Boolean.FALSE, cors.getAllowCredentials(),
                    "Cookie credentials must remain disabled until CSRF and session transport are defined");
        }
    }

    @Test
    void unknownOriginIsNotAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/users/login");
        request.addHeader("Origin", "https://untrusted.example.invalid");
        request.addHeader("Access-Control-Request-Method", "POST");

        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);
        assertNotNull(cors);
        assertNull(cors.checkOrigin("https://untrusted.example.invalid"));
        assertEquals(Boolean.FALSE, cors.getAllowCredentials());
    }
}
