package com.todo.user.service;

import com.todo.user.repository.RefreshSessionStore;
import com.todo.user.security.RefreshSession;
import com.todo.user.security.RefreshTokenMaterial;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues a first opaque refresh session for an enabled account.
 *
 * Internal domain operation only: it does not change login responses,
 * issue cookies, or expose an HTTP endpoint.
 */
@Service
public class RefreshSessionIssuanceService {
    private final RefreshSessionStore store;
    private final Clock clock;
    private final Duration lifetime;

    @Autowired
    public RefreshSessionIssuanceService(
            RefreshSessionStore store,
            @Value("${jwt.refresh-session-lifetime-seconds:604800}") long lifetimeSeconds) {
        this(store, Clock.systemUTC(), Duration.ofSeconds(lifetimeSeconds));
    }

    RefreshSessionIssuanceService(RefreshSessionStore store, Clock clock, Duration lifetime) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Refresh session lifetime must be positive");
        }
    }

    public Optional<IssuedSession> issue(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        String rawToken = RefreshTokenMaterial.generate();
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(lifetime);
        RefreshSession session = new RefreshSession(
                sessionId, userId, familyId,
                RefreshTokenMaterial.fingerprint(rawToken), expiresAt, null);

        if (!store.createForEnabledUser(session)) {
            return Optional.empty();
        }
        return Optional.of(new IssuedSession(sessionId, rawToken, expiresAt));
    }

    /**
     * Raw token is returned only to the immediate internal caller.
     * Never log it, include it in spans, or persist it in plaintext.
     */
    public record IssuedSession(UUID sessionId, String token, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedSession[sessionId=" + sessionId +
                    ", token=[REDACTED], expiresAt=" + expiresAt + "]";
        }
    }
}
