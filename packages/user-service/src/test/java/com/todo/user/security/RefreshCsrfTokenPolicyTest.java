package com.todo.user.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class RefreshCsrfTokenPolicyTest {
    private static final byte[] SECRET_A =
            "csrf-test-key-a-is-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECRET_B =
            "csrf-test-key-b-is-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8);

    @Test
    void tokenIsBoundToCurrentRefreshTokenAndSecret() {
        var policy = new RefreshCsrfTokenPolicy(SECRET_A);
        String first = RefreshTokenMaterial.generate();
        String successor = RefreshTokenMaterial.generate();

        String csrf = policy.issue(first);
        assertTrue(policy.permits(first, csrf));
        assertEquals(csrf, policy.issue(first), "same refresh token yields stable CSRF value");
        assertFalse(csrf.contains(first), "CSRF must not reveal raw refresh material");
        assertFalse(policy.permits(successor, csrf), "rotation must invalidate old CSRF token");
        assertFalse(new RefreshCsrfTokenPolicy(SECRET_B).permits(first, csrf),
                "tokens must not validate with another signing key");
        assertTrue(policy.permits(successor, policy.issue(successor)));
    }

    @Test
    void rejectsMissingMalformedTamperedAndRawTokens() {
        var policy = new RefreshCsrfTokenPolicy(SECRET_A);
        String refresh = RefreshTokenMaterial.generate();
        String csrf = policy.issue(refresh);

        assertFalse(policy.permits(refresh, null));
        assertFalse(policy.permits(refresh, ""));
        assertFalse(policy.permits(refresh, "not-a-valid-csrf"));
        assertFalse(policy.permits(refresh, csrf + "="));
        assertFalse(policy.permits(refresh, refresh));
        assertFalse(policy.permits(null, csrf));
        assertFalse(policy.permits("", csrf));
        assertFalse(policy.permits("malformed", csrf));

        byte[] modified = Base64.getUrlDecoder().decode(csrf);
        modified[0] ^= 1;
        assertFalse(policy.permits(refresh,
                Base64.getUrlEncoder().withoutPadding().encodeToString(modified)));
    }

    @Test
    void rejectsWeakSecretAndProtectsAgainstExternalSecretArrayMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshCsrfTokenPolicy(new byte[31]));
        assertThrows(NullPointerException.class,
                () -> new RefreshCsrfTokenPolicy(null));

        byte[] mutableSecret = SECRET_A.clone();
        var policy = new RefreshCsrfTokenPolicy(mutableSecret);
        String refresh = RefreshTokenMaterial.generate();
        String csrf = policy.issue(refresh);
        Arrays.fill(mutableSecret, (byte) 0);
        assertTrue(policy.permits(refresh, csrf),
                "mutating caller's original key array must not change the policy");
    }

    @Test
    void rejectsInvalidRefreshTokenBeforeIssuance() {
        var policy = new RefreshCsrfTokenPolicy(SECRET_A);
        assertThrows(IllegalArgumentException.class, () -> policy.issue(null));
        assertThrows(IllegalArgumentException.class, () -> policy.issue(""));
        assertThrows(IllegalArgumentException.class, () -> policy.issue("invalid"));
    }
}
