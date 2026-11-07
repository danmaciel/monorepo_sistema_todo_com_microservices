package com.todo.user.controller;

import com.todo.user.security.RefreshCookiePolicy;
import com.todo.user.service.RefreshSessionRotationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guarded refresh-family logout. The registered RefreshHttpBoundaryFilter is
 * fail-closed by default and must validate Origin, cookie and CSRF before any
 * request can reach this controller.
 *
 * This only revokes refresh sessions; existing access JWTs remain valid until
 * their normal expiry. The browser refresh flow has not been enabled.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class RefreshLogoutController {
    private final RefreshSessionRotationService sessions;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // The boundary filter has already checked uniqueness and CSRF binding.
        // Do not echo raw refresh material in response bodies, logs or tracing.
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (RefreshCookiePolicy.COOKIE_NAME.equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token != null && !token.isBlank()) {
            sessions.revokeFamily(token);
        }

        // No session-existence oracle: revocation is intentionally idempotent.
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, RefreshCookiePolicy.clear().toString())
                .build();
    }
}
