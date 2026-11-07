package com.todo.user.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable description of refresh-session identity, fingerprint and expiration.
 *
 * The PostgreSQL store handles persistence, one-time consumption and rotation.
 * This record alone does not check the consumed state or prevent replay.
 * Refresh sessions are not yet exposed by the authentication HTTP API.
 */
public record RefreshSession(
        UUID id,
        UUID userId,
        UUID familyId,
        String tokenFingerprint,
        Instant expiresAt,
        Instant revokedAt
) {
    public RefreshSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (tokenFingerprint == null || !tokenFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-256 token fingerprint");
        }
    }

    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean matches(String rawToken) {
        return RefreshTokenMaterial.matches(rawToken, tokenFingerprint);
    }
}
