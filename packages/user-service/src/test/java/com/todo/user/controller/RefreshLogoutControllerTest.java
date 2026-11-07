package com.todo.user.controller;

import com.todo.user.security.RefreshCookiePolicy;
import com.todo.user.security.RefreshCsrfTokenPolicy;
import com.todo.user.security.RefreshHttpBoundaryFilter;
import com.todo.user.security.RefreshTokenMaterial;
import com.todo.user.service.RefreshSessionRotationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefreshLogoutControllerTest {
    private static final String ORIGIN = "https://app.example.test";
    private static final byte[] KEY = "independent-csrf-signing-key-for-tests".getBytes(StandardCharsets.UTF_8);
    private static final String KEY_B64 = Base64.getEncoder().encodeToString(KEY);
    private final RefreshSessionRotationService sessions = mock(RefreshSessionRotationService.class);

    private MockMvc http(boolean enabled) {
        var filter = new RefreshHttpBoundaryFilter(
                enabled, enabled ? ORIGIN : "", enabled ? KEY_B64 : "");
        return MockMvcBuilders.standaloneSetup(new RefreshLogoutController(sessions))
                .addFilters(filter).build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder valid(String token) {
        return post("/users/logout").servletPath("/users/logout")
                .header("Origin", ORIGIN)
                .header(RefreshCsrfTokenPolicy.HEADER_NAME,
                        new RefreshCsrfTokenPolicy(KEY).issue(token))
                .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, token));
    }

    @Test
    void disabledRouteNeverInvokesSessionRevocation() throws Exception {
        String token = RefreshTokenMaterial.generate();
        http(false).perform(valid(token))
                .andExpect(status().isNotFound());
        verifyNoInteractions(sessions);
    }

    @Test
    void validCookieOriginAndCsrfRevokeFamilyAndClearCookie() throws Exception {
        String token = RefreshTokenMaterial.generate();
        when(sessions.revokeFamily(token)).thenReturn(2);

        var response = http(true).perform(valid(token))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Set-Cookie", containsString("__Secure-todo-refresh=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/users")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(content().string(""))
                .andReturn();

        assertEquals(204, response.getResponse().getStatus());
        verify(sessions).revokeFamily(token);
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void unknownSessionDoesNotRevealExistenceAndStillClearsCookie() throws Exception {
        String token = RefreshTokenMaterial.generate();
        when(sessions.revokeFamily(token)).thenReturn(0);

        http(true).perform(valid(token))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(content().string(""));
        verify(sessions).revokeFamily(token);
    }

    @Test
    void missingOrWrongCsrfAndOriginCannotInvokeRevocation() throws Exception {
        String token = RefreshTokenMaterial.generate();
        MockMvc mvc = http(true);
        mvc.perform(post("/users/logout").servletPath("/users/logout")
                        .header("Origin", ORIGIN)
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, token)))
                .andExpect(status().isForbidden());
        mvc.perform(valid(token).header("Origin", "https://evil.example.invalid"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/logout").servletPath("/users/logout")
                        .header("Origin", ORIGIN)
                        .header(RefreshCsrfTokenPolicy.HEADER_NAME, "malformed")
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, token)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sessions);
    }
}
