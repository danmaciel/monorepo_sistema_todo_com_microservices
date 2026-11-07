package com.todo.todo.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderContractTest {
    private static final String SECRET = "jwt-test-key-isolated-from-runtime-2026-11-06-secret";
    private static final String OTHER_SECRET = "jwt-other-isolated-from-runtime-2026-11-06-secret";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET);

    @Test
    void acceptsSignedAccessTokenAndReadsSubject() {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("username", "tester")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(provider.validateToken(token));
        assertEquals(userId, provider.getUserIdFromToken(token));
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
    void rejectsTokenSignedByDifferentKey() {
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
    void acceptsNewIssuedContractWhileLegacyTokensRemainValid() {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer("todo-user-service")
                .audience().add("todo-api").and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(provider.validateToken(token));
        assertEquals(userId, provider.getUserIdFromToken(token));
    }

    @Test
    void rejectsTokenWithUnexpectedIssuerEvenWhenSignedCorrectly() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("another-service")
                .audience().add("todo-api").and()
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    void rejectsTokenWithUnexpectedAudienceEvenWhenSignedCorrectly() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("todo-user-service")
                .audience().add("another-api").and()
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    void rejectsPartialIssuerAudienceContract() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("todo-user-service")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    void strictModeRejectsLegacyTokenMissingIssuerAndAudience() {
        String legacy = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(provider.validateToken(legacy));
        ReflectionTestUtils.setField(provider, "acceptLegacyWithoutClaims", false);
        assertFalse(provider.validateToken(legacy));
    }
}
