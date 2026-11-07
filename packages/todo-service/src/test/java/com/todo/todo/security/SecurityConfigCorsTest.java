package com.todo.todo.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig(mock(JwtAuthenticationFilter.class));

    @Test
    void allowsOnlyConfiguredLocalFrontendOrigins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/todos");

        CorsConfiguration cors = securityConfig.corsConfigurationSource().getCorsConfiguration(request);

        assertNotNull(cors);
        assertEquals("http://localhost:3000", cors.checkOrigin("http://localhost:3000"));
        assertEquals("http://localhost:5173", cors.checkOrigin("http://localhost:5173"));
        assertNull(cors.checkOrigin("https://untrusted.example"));
        assertNull(cors.checkOrigin("http://localhost:9999"));
        assertFalse(cors.getAllowedOrigins().contains("*"));
    }
}
