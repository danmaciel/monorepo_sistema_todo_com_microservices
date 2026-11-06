package com.todo.user.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable description of a refresh session; not yet persisted or exposed by an API.
 *
 * Session state must be checked and replaced under an atomic database transaction
 * when refresh rotation is implemented. This model alone does not prevent replay.
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
