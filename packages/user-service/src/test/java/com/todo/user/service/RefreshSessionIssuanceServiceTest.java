package com.todo.user.service;

import com.todo.user.repository.RefreshSessionStore;
import com.todo.user.security.RefreshSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshSessionIssuanceServiceTest {

    @Test
    void issuanceResultRedactsRawTokenInTextualRepresentation() {
        RefreshSessionStore store = mock(RefreshSessionStore.class);
        when(store.createForEnabledUser(any(RefreshSession.class))).thenReturn(true);

        var issuer = new RefreshSessionIssuanceService(store, 600L);
        var result = issuer.issue(UUID.randomUUID()).orElseThrow();

        String representation = result.toString();
        assertFalse(representation.contains(result.token()));
        assertTrue(representation.contains("[REDACTED]"));
        assertTrue(representation.contains(result.sessionId().toString()));
    }

    @Test
    void rejectsNonPositiveSessionLifetime() {
        RefreshSessionStore store = mock(RefreshSessionStore.class);

        assertThrows(IllegalArgumentException.class,
                () -> new RefreshSessionIssuanceService(store, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshSessionIssuanceService(store, -1L));
        verifyNoInteractions(store);
    }

    @Test
    void unsuccessfulStorageDoesNotReturnToken() {
        RefreshSessionStore store = mock(RefreshSessionStore.class);
        when(store.createForEnabledUser(any(RefreshSession.class))).thenReturn(false);

        var issuer = new RefreshSessionIssuanceService(store, 600L);

        assertTrue(issuer.issue(UUID.randomUUID()).isEmpty());
    }
}
