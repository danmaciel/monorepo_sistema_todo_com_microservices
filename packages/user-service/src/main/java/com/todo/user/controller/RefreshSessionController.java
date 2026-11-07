package com.todo.user.controller;

import com.todo.user.dto.AuthResponse;
import com.todo.user.dto.LoginRequest;
import com.todo.user.entity.User;
import com.todo.user.repository.UserRepository;
import com.todo.user.security.JwtTokenProvider;
import com.todo.user.security.RefreshCookiePolicy;
import com.todo.user.security.RefreshCsrfTokenPolicy;
import com.todo.user.security.RefreshHttpBoundaryFilter;
import com.todo.user.service.RefreshSessionIssuanceService;
import com.todo.user.service.RefreshSessionRotationService;
import com.todo.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Explicit opt-in session API for demos/integration tests.
 *
 * The registered RefreshHttpBoundaryFilter enforces a disabled-by-default
 * feature gate, exact Origin and refresh-cookie/CSRF validation.
 * Regular /users/login and /users/register remain unchanged.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class RefreshSessionController {
    private final UserService users;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwt;
    private final RefreshSessionIssuanceService issuance;
    private final RefreshSessionRotationService rotations;
    private final RefreshHttpBoundaryFilter boundary;

    /**
     * Authenticates credentials, creates a refresh session and delivers
     * access JWT plus CSRF token. Never exposes raw refresh material in JSON.
     */
    @PostMapping("/session/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        requireEnabled();
        AuthResponse auth = users.login(request);
        var issued = issuance.issue(auth.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("Cannot issue a refresh session"));
        return sessionResponse(auth, issued.token(), issued.expiresAt());
    }

    /** A consumed/unknown/disabled refresh session cannot mint an access JWT. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookiePolicy.COOKIE_NAME) String token) {
        requireEnabled();
        var rotated = rotations.rotate(token);
        if (rotated.isEmpty()) {
            return unauthorized();
        }
        var next = rotated.orElseThrow();
        User user = userRepository.findById(next.userId())
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElse(null);
        if (user == null) {
            // Defensive cleanup if account was disabled after rotation.
            rotations.revokeFamily(next.token());
            return unauthorized();
        }
        String access = jwt.generateToken(user.getId(), user.getUsername());
        AuthResponse auth = AuthResponse.of(access, users.getCurrentUser(user));
        return sessionResponse(auth, next.token(), next.expiresAt());
    }

    private void requireEnabled() {
        if (!boundary.isEnabled()) {
            throw new IllegalStateException("Refresh HTTP is disabled");
        }
    }

    private ResponseEntity<AuthResponse> sessionResponse(
            AuthResponse auth, String refreshToken, Instant expiresAt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                        RefreshCookiePolicy.issue(refreshToken, expiresAt, Instant.now()).toString())
                .header(RefreshCsrfTokenPolicy.HEADER_NAME, boundary.issueCsrf(refreshToken))
                .body(auth);
    }

    private ResponseEntity<AuthResponse> unauthorized() {
        return ResponseEntity.status(401)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, RefreshCookiePolicy.clear().toString())
                .build();
    }
}
