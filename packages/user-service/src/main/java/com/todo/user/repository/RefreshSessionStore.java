package com.todo.user.repository;

import com.todo.user.security.RefreshSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

/**
 * PostgreSQL-backed refresh-session persistence.
 *
 * Consuming a token is a single conditional UPDATE. Concurrent consumers of
 * the same token cannot both succeed, even across application instances.
 * The internal rotation service composes claim and successor insertion in one transaction.
 * HTTP refresh/login integration and revocation policy are not implemented yet.
 */
@Repository
public class RefreshSessionStore {
    private final JdbcTemplate jdbc;

    public RefreshSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(RefreshSession session) {
        Objects.requireNonNull(session, "session");
        if (session.revokedAt() != null) {
            throw new IllegalArgumentException("New sessions cannot start revoked");
        }
        jdbc.update(
                "INSERT INTO refresh_sessions (id, user_id, family_id, token_fingerprint, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                session.id(), session.userId(), session.familyId(), session.tokenFingerprint(),
                Timestamp.from(session.expiresAt()));
    }

    /**
     * Claims a valid parent token and returns its trusted database identity.
     * Must be called inside the same transaction as insertion of its successor.
     * No row is returned for missing, consumed, revoked or expired tokens, or
     * when the owning account is disabled/deleted. A row-share lock serializes
     * this check against concurrent account updates.
     */
    public Optional<RefreshSession> claimForRotation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String fingerprint = com.todo.user.security.RefreshTokenMaterial.fingerprint(rawToken);
        List<RefreshSession> claimed = jdbc.query(
                "UPDATE refresh_sessions SET consumed_at = CURRENT_TIMESTAMP " +
                        "WHERE token_fingerprint = ? AND consumed_at IS NULL " +
                        "AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP " +
                        "AND EXISTS (SELECT 1 FROM users u " +
                        "WHERE u.id = refresh_sessions.user_id AND u.enabled = TRUE FOR SHARE) " +
                        "RETURNING id, user_id, family_id, token_fingerprint, expires_at, revoked_at",
                (rs, rowNum) -> new RefreshSession(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("family_id", UUID.class),
                        rs.getString("token_fingerprint"),
                        rs.getTimestamp("expires_at").toInstant(),
                        null),
                fingerprint);
        return claimed.stream().findFirst();
    }

    /**
     * Revoke the entire token family if the presented token has already been
     * consumed and is still inside its absolute lifetime.
     *
     * A random/unknown token, an expired token, or a token already revoked
     * must not affect any family. This is called only after a failed claim;
     * the successful rotation has already committed at that point.
     *
     * @return number of newly revoked sessions in the affected family
     */
    public int revokeFamilyOnReplay(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return 0;
        }
        String fingerprint = com.todo.user.security.RefreshTokenMaterial.fingerprint(rawToken);
        return jdbc.update(
                "UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP " +
                        "WHERE family_id = (SELECT family_id FROM refresh_sessions " +
                        "WHERE token_fingerprint = ? AND consumed_at IS NOT NULL " +
                        "AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP) " +
                        "AND revoked_at IS NULL",
                fingerprint);
    }

    /**
     * Atomically marks a still-active token as consumed. The database clock
     * determines expiration, preventing application-server clock drift.
     *
     * @return true exactly once for an eligible fingerprint, false otherwise
     */
    public boolean consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String fingerprint = com.todo.user.security.RefreshTokenMaterial.fingerprint(rawToken);
        int updated = jdbc.update(
                "UPDATE refresh_sessions SET consumed_at = CURRENT_TIMESTAMP " +
                        "WHERE token_fingerprint = ? " +
                        "AND consumed_at IS NULL AND revoked_at IS NULL " +
                        "AND expires_at > CURRENT_TIMESTAMP",
                fingerprint);
        return updated == 1;
    }
}
