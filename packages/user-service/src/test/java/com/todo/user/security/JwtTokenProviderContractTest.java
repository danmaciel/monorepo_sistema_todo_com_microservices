package com.todo.user.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderContractTest {
    private static final String SECRET = "jwt-test-key-isolated-from-runtime-2026-11-06-secret";
    private static final String OTHER_SECRET = "jwt-other-isolated-from-runtime-2026-11-06-secret";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000L);

    @Test
    void issuedTokenContainsExpectedIdentityAndCanBeValidated() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, "tester");

        assertTrue(provider.validateToken(token));
        assertEquals(userId, provider.getUserIdFromToken(token));
        assertEquals("tester", provider.getUsernameFromToken(token));
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals("todo-user-service", claims.getIssuer());
        assertTrue(claims.getAudience().contains("todo-api"));
    }

    @Test
    void rejectsExpiredSignedToken() {
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis() - 120_000L))
                .expiration(new Date(System.currentTimeMillis() - 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(expired));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String forged = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(forged));
    }

    @Test
    void rejectsMalformedToken() {
        assertFalse(provider.validateToken("not.a.jwt"));
    }

    @Test
    void rejectsSignedTokenWithoutExpiration() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    void rejectsSignedTokenWithoutSubject() {
        String token = Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    void rejectsSignedTokenWithNonUuidSubject() {
        String token = Jwts.builder()
                .subject("not-a-uuid")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    void acceptsPreviouslyIssuedTokenWithoutIssuerOrAudienceDuringTransition() {
        UUID userId = UUID.randomUUID();
        String legacy = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(provider.validateToken(legacy));
        assertEquals(userId, provider.getUserIdFromToken(legacy));
    }
}
