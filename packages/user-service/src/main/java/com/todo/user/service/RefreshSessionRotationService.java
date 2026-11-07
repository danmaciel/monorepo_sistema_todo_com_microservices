package com.todo.user.service;

import com.todo.user.repository.RefreshSessionStore;
import com.todo.user.security.RefreshSession;
import com.todo.user.security.RefreshTokenMaterial;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Rotates an existing opaque refresh token in one database transaction.
 *
 * This is an internal service, not an authentication endpoint. The PostgreSQL
 * store checks account eligibility and revokes the family on reuse of a consumed
 * token. Initial issuance, browser transport, HTTP refresh and logout remain separate.
 */
@Service
public class RefreshSessionRotationService {
    private final RefreshSessionStore store;
    private final TransactionTemplate transactions;

    public RefreshSessionRotationService(RefreshSessionStore store, PlatformTransactionManager transactionManager) {
        this.store = store;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Optional<RotationResult> rotate(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }

        return transactions.execute(status -> {
            if (!store.lockFamilyForPresentedToken(presentedToken)) {
                return Optional.empty();
            }

            Optional<RotationResult> rotated = store.claimForRotation(presentedToken).map(parent -> {
                String nextToken = RefreshTokenMaterial.generate();
                UUID nextSessionId = UUID.randomUUID();
                store.create(new RefreshSession(
                        nextSessionId,
                        parent.userId(),
                        parent.familyId(),
                        RefreshTokenMaterial.fingerprint(nextToken),
                        parent.expiresAt(),
                        null));
                return new RotationResult(nextSessionId, nextToken);
            });

            // The same family lock protects both successor insertion and replay
            // revocation. A concurrent claimant cannot insert after revocation.
            if (rotated.isEmpty()) {
                store.revokeFamilyOnReplay(presentedToken);
            }
            return rotated;
        });
    }

    /**
     * Internal family revocation for a future logout flow.
     * Serializes against rotation in the same PostgreSQL transaction.
     * Does not revoke issued access JWTs or expose an HTTP endpoint.
     */
    public int revokeFamily(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return 0;
        }
        Integer revoked = transactions.execute(status -> {
            if (!store.lockFamilyForPresentedToken(presentedToken)) {
                return 0;
            }
            return store.revokeFamilyByPresentedToken(presentedToken);
        });
        return revoked == null ? 0 : revoked;
    }

    /**
     * Carries the successor's raw token only to the immediate caller.
     * It must never be logged, persisted in plaintext or placed in tracing attributes.
     */
    public record RotationResult(UUID sessionId, String token) {
        @Override
        public String toString() {
            return "RotationResult[sessionId=" + sessionId + ", token=[REDACTED]]";
        }
    }
}
