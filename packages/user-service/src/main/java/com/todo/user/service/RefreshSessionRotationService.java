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
 * This is a domain service, not an authentication endpoint. Token issuance,
 * browser transport, account checks and replay response policy remain separate.
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

        return transactions.execute(status -> store.claimForRotation(presentedToken).map(parent -> {
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
