package com.todo.user.controller;

import com.todo.user.dto.AuthResponse;
import com.todo.user.dto.LoginRequest;
import com.todo.user.dto.UserResponse;
import com.todo.user.entity.User;
import com.todo.user.repository.UserRepository;
import com.todo.user.repository.RefreshSessionStore;
import com.todo.user.security.JwtTokenProvider;
import com.todo.user.security.RefreshCookiePolicy;
import com.todo.user.security.RefreshCsrfTokenPolicy;
import com.todo.user.security.RefreshHttpBoundaryFilter;
import com.todo.user.security.RefreshTokenMaterial;
import com.todo.user.service.RefreshSessionIssuanceService;
import com.todo.user.service.RefreshSessionRotationService;
import com.todo.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefreshSessionControllerTest {
    private static final String ORIGIN = "https://app.example.test";
    private static final byte[] KEY = "session-csrf-test-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String KEY_B64 = Base64.getEncoder().encodeToString(KEY);

    private final UserService users = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    private final RefreshSessionIssuanceService issuance = mock(RefreshSessionIssuanceService.class);
    private final RefreshSessionRotationService rotations = mock(RefreshSessionRotationService.class);
    private final RefreshSessionStore refreshSessions = mock(RefreshSessionStore.class);

    private MockMvc mvc(boolean enabled) {
        var boundary = new RefreshHttpBoundaryFilter(
                enabled, enabled ? ORIGIN : "", enabled ? KEY_B64 : "");
        return MockMvcBuilders.standaloneSetup(
                        new RefreshSessionController(users, userRepository, jwt,
                                issuance, rotations, refreshSessions, boundary))
                .addFilters(boundary).build();
    }

    @Test
    void csrfBootstrapRecoversOnlyActiveSessionProof() throws Exception {
        String token = RefreshTokenMaterial.generate();
        when(refreshSessions.isActive(token)).thenReturn(true);
        mvc(true).perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/users/session/csrf").servletPath("/users/session/csrf")
                        .header("Origin", ORIGIN)
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, token)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string(RefreshCsrfTokenPolicy.HEADER_NAME,
                        new RefreshCsrfTokenPolicy(KEY).issue(token)))
                .andExpect(content().string(""));
        verify(refreshSessions).isActive(token);
        verifyNoInteractions(rotations, jwt);
    }

    @Test
    void csrfBootstrapRejectsMissingOriginOrRevokedCookie() throws Exception {
        String token = RefreshTokenMaterial.generate();
        mvc(true).perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/users/session/csrf").servletPath("/users/session/csrf")
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, token)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(refreshSessions);
        when(refreshSessions.isActive(token)).thenReturn(false);
        mvc(true).perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/users/session/csrf").servletPath("/users/session/csrf")
                        .header("Origin", ORIGIN)
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        verify(refreshSessions).isActive(token);
    }

    @Test
    void sessionLoginIsBlockedByDefault() throws Exception {
        mvc(false).perform(post("/users/session/login").servletPath("/users/session/login")
                        .header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"password\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(users, issuance, rotations);
    }

    @Test
    void originGuardPreventsSessionCreation() throws Exception {
        mvc(true).perform(post("/users/session/login").servletPath("/users/session/login")
                        .header("Origin", "https://evil.example.invalid")
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users, issuance, rotations);
    }

    @Test
    void sessionLoginIssuesAccessJwtCookieAndCsrfButNotRawRefreshInBody() throws Exception {
        UUID userId = UUID.randomUUID();
        String refresh = RefreshTokenMaterial.generate();
        Instant expiry = Instant.now().plusSeconds(3600);
        var auth = AuthResponse.of("access-jwt", UserResponse.builder()
                .id(userId).username("demo").build());
        when(users.login(any(LoginRequest.class))).thenReturn(auth);
        when(issuance.issue(userId)).thenReturn(Optional.of(
                new RefreshSessionIssuanceService.IssuedSession(UUID.randomUUID(), refresh, expiry)));

        var result = mvc(true).perform(post("/users/session/login")
                        .servletPath("/users/session/login")
                        .header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string(RefreshCsrfTokenPolicy.HEADER_NAME,
                        new RefreshCsrfTokenPolicy(KEY).issue(refresh)))
                .andExpect(jsonPath("$.token").value("access-jwt"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(refresh));
        assertTrue(result.getResponse().getHeader("Set-Cookie").contains(refresh));
        verify(users).login(any(LoginRequest.class));
        verify(issuance).issue(userId);
    }

    @Test
    void refreshRotatesAndIssuesNewJwtBoundToTheSuccessor() throws Exception {
        UUID userId = UUID.randomUUID();
        String old = RefreshTokenMaterial.generate();
        String next = RefreshTokenMaterial.generate();
        Instant expiry = Instant.now().plusSeconds(3600);
        var user = User.builder().id(userId).username("demo").enabled(true).build();
        when(rotations.rotate(old)).thenReturn(Optional.of(
                new RefreshSessionRotationService.RotationResult(
                        UUID.randomUUID(), next, userId, expiry)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwt.generateToken(userId, "demo")).thenReturn("fresh-access-jwt");
        when(users.getCurrentUser(user)).thenReturn(UserResponse.builder()
                .id(userId).username("demo").build());

        var result = mvc(true).perform(post("/users/refresh").servletPath("/users/refresh")
                        .header("Origin", ORIGIN)
                        .header(RefreshCsrfTokenPolicy.HEADER_NAME,
                                new RefreshCsrfTokenPolicy(KEY).issue(old))
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, old)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString(next)))
                .andExpect(header().string(RefreshCsrfTokenPolicy.HEADER_NAME,
                        new RefreshCsrfTokenPolicy(KEY).issue(next)))
                .andExpect(jsonPath("$.token").value("fresh-access-jwt"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(next));
        verify(rotations).rotate(old);
    }

    @Test
    void rejectedRotationClearsCookieWithoutMintingJwt() throws Exception {
        String old = RefreshTokenMaterial.generate();
        when(rotations.rotate(old)).thenReturn(Optional.empty());

        mvc(true).perform(post("/users/refresh").servletPath("/users/refresh")
                        .header("Origin", ORIGIN)
                        .header(RefreshCsrfTokenPolicy.HEADER_NAME,
                                new RefreshCsrfTokenPolicy(KEY).issue(old))
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, old)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        verifyNoInteractions(jwt, userRepository);
    }

    @Test
    void invalidCsrfNeverRotatesSession() throws Exception {
        String old = RefreshTokenMaterial.generate();
        mvc(true).perform(post("/users/refresh").servletPath("/users/refresh")
                        .header("Origin", ORIGIN)
                        .header(RefreshCsrfTokenPolicy.HEADER_NAME, "invalid")
                        .cookie(new Cookie(RefreshCookiePolicy.COOKIE_NAME, old)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rotations, jwt, users);
    }
}
