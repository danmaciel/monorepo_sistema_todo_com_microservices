package com.todo.user.service;

import com.todo.user.security.RefreshTokenMaterial;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshSessionRotationResultTest {

    @Test
    void textualRepresentationMustNotExposeTheRefreshToken() {
        String token = RefreshTokenMaterial.generate();
        UUID sessionId = UUID.randomUUID();

        var result = new RefreshSessionRotationService.RotationResult(sessionId, token);
        String representation = result.toString();

        assertTrue(representation.contains(sessionId.toString()));
        assertTrue(representation.contains("[REDACTED]"));
        assertFalse(representation.contains(token), "Refresh token must not appear in record toString()");
        assertEquals(token, result.token(), "Token must still be available to the intended caller");
    }
}
