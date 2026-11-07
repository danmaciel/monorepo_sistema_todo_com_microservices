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

        Optional<RotationResult> rotated = transactions.execute(status -> store.claimForRotation(presentedToken).map(parent -> {
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
        }));

        // Run replay handling after the rotation transaction completes. A
        // concurrent successful claimant must commit its successor before we
        // revoke the family; never revoke in a transaction that is rolled back.
        if (rotated.isEmpty()) {
            store.revokeFamilyOnReplay(presentedToken);
        }
        return rotated;
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
