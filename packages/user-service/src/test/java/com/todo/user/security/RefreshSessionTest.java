package com.todo.user.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshSessionTest {
    @Test
    void generatedTokensContain256BitsOfRandomMaterial() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = RefreshTokenMaterial.generate();
            assertEquals(32, Base64.getUrlDecoder().decode(token).length);
            assertTrue(tokens.add(token), "generated tokens should not repeat in this sample");
        }
    }

    @Test
    void fingerprintDoesNotStoreTheRawTokenAndMatchesOnlyItsSource() {
        String token = RefreshTokenMaterial.generate();
        String fingerprint = RefreshTokenMaterial.fingerprint(token);

        assertEquals(64, fingerprint.length());
        assertNotEquals(token, fingerprint);
        assertTrue(RefreshTokenMaterial.matches(token, fingerprint));
        assertFalse(RefreshTokenMaterial.matches(RefreshTokenMaterial.generate(), fingerprint));
    }

    @Test
    void expiredOrRevokedSessionsAreNotActive() {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        String fingerprint = RefreshTokenMaterial.fingerprint(RefreshTokenMaterial.generate());
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        RefreshSession active = new RefreshSession(id, userId, familyId, fingerprint, now.plusSeconds(60), null);
        RefreshSession expired = new RefreshSession(id, userId, familyId, fingerprint, now, null);
        RefreshSession revoked = new RefreshSession(id, userId, familyId, fingerprint, now.plusSeconds(60), now.minusSeconds(30));

        assertTrue(active.isActiveAt(now));
        assertFalse(expired.isActiveAt(now));
        assertFalse(revoked.isActiveAt(now));
    }

    @Test
    void sessionRecognizesItsOwnTokenWithoutKeepingIt() {
        String token = RefreshTokenMaterial.generate();
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                RefreshTokenMaterial.fingerprint(token),
                Instant.parse("2027-01-01T00:00:00Z"),
                null);

        assertTrue(session.matches(token));
        assertFalse(session.matches(RefreshTokenMaterial.generate()));
        assertFalse(session.matches(null));
        assertFalse(RefreshTokenMaterial.matches(null, session.tokenFingerprint()));
    }

    @Test
    void invalidSessionFingerprintOrMissingValuesAreRejected() {
        Instant expiry = Instant.parse("2027-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "not-a-hash", expiry, null));
        assertThrows(NullPointerException.class,
                () -> new RefreshSession(UUID.randomUUID(), null, UUID.randomUUID(), "a".repeat(64), expiry, null));
        assertThrows(IllegalArgumentException.class, () -> RefreshTokenMaterial.fingerprint(""));
    }
}
