package com.todo.user.repository;

import com.todo.user.security.RefreshSession;
import com.todo.user.security.RefreshTokenMaterial;
import com.todo.user.service.RefreshSessionRotationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.mockito.Mockito.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RefreshSessionStoreTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("refresh_test")
            .withUsername("refresh_test")
            .withPassword("local-test-password-only");

    static JdbcTemplate jdbc;
    static RefreshSessionStore store;
    static DriverManagerDataSource dataSource;
    static RefreshSessionRotationService rotations;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        // This isolated Testcontainers schema models only the User fields required
        // by refresh authorization; production users remain Hibernate-managed.
        jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY, enabled BOOLEAN NOT NULL)");
        store = new RefreshSessionStore(jdbc);
        rotations = new RefreshSessionRotationService(store, new DataSourceTransactionManager(dataSource));
    }

    private static String createActiveToken() {
        String token = RefreshTokenMaterial.generate();
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, enabled) VALUES (?, TRUE)", userId);
        store.create(new RefreshSession(
                UUID.randomUUID(), userId, UUID.randomUUID(),
                RefreshTokenMaterial.fingerprint(token), Instant.now().plusSeconds(600), null));
        return token;
    }

    @Test
    void persistsOnlyFingerprintAndTokenIsConsumedOnce() {
        String token = createActiveToken();
        String fingerprint = RefreshTokenMaterial.fingerprint(token);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_sessions WHERE token_fingerprint = ?",
                Integer.class, fingerprint);
        assertEquals(1, count);
        assertTrue(store.consume(token));
        assertFalse(store.consume(token));
        assertFalse(store.consume("not-found-token"));

        Integer consumed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_sessions WHERE token_fingerprint = ? AND consumed_at IS NOT NULL",
                Integer.class, fingerprint);
        assertEquals(1, consumed);
    }

    @Test
    void concurrentConsumptionAllowsExactlyOneWinner() throws Exception {
        String token = createActiveToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start gate timed out");
                }
                return store.consume(token);
            };

            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both callers must reach start gate");
            start.countDown();

            int successful = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful, "PostgreSQL conditional UPDATE must admit only one consumer");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredAndRevokedSessionsCannotBeConsumed() {
        String expired = RefreshTokenMaterial.generate();
        store.create(new RefreshSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RefreshTokenMaterial.fingerprint(expired), Instant.now().plusSeconds(60), null));
        jdbc.update("UPDATE refresh_sessions SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes', " +
                        "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                        "WHERE token_fingerprint = ?", RefreshTokenMaterial.fingerprint(expired));
        assertFalse(store.consume(expired));

        String revoked = createActiveToken();
        jdbc.update("UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP " +
                        "WHERE token_fingerprint = ?", RefreshTokenMaterial.fingerprint(revoked));
        assertFalse(store.consume(revoked));
    }

    @Test
    void rotationReplacesTokenAndPreservesAccountFamilyAndAbsoluteExpiry() {
        String parent = createActiveToken();
        String parentFingerprint = RefreshTokenMaterial.fingerprint(parent);
        UUID owner = jdbc.queryForObject(
                "SELECT user_id FROM refresh_sessions WHERE token_fingerprint = ?",
                UUID.class, parentFingerprint);
        UUID family = jdbc.queryForObject(
                "SELECT family_id FROM refresh_sessions WHERE token_fingerprint = ?",
                UUID.class, parentFingerprint);
        Instant expiry = jdbc.queryForObject(
                "SELECT expires_at FROM refresh_sessions WHERE token_fingerprint = ?",
                (rs, rowNum) -> rs.getTimestamp(1).toInstant(), parentFingerprint);

        var next = rotations.rotate(parent).orElseThrow();

        assertFalse(rotations.rotate(parent).isPresent(), "consumed parent cannot rotate twice");
        assertNotEquals(parent, next.token());
        assertTrue(RefreshTokenMaterial.matches(next.token(),
                jdbc.queryForObject(
                        "SELECT token_fingerprint FROM refresh_sessions WHERE id = ?",
                        String.class, next.sessionId())));
        assertEquals(owner, jdbc.queryForObject(
                "SELECT user_id FROM refresh_sessions WHERE id = ?",
                UUID.class, next.sessionId()));
        assertEquals(family, jdbc.queryForObject(
                "SELECT family_id FROM refresh_sessions WHERE id = ?",
                UUID.class, next.sessionId()));
        assertEquals(expiry, jdbc.queryForObject(
                "SELECT expires_at FROM refresh_sessions WHERE id = ?",
                (rs, rowNum) -> rs.getTimestamp(1).toInstant(), next.sessionId()));
    }

    @Test
    void disabledAccountCannotRotateOrCreateASuccessor() {
        String parent = createActiveToken();
        String fingerprint = RefreshTokenMaterial.fingerprint(parent);
        UUID owner = jdbc.queryForObject(
                "SELECT user_id FROM refresh_sessions WHERE token_fingerprint = ?",
                UUID.class, fingerprint);
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", owner);

        assertTrue(rotations.rotate(parent).isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_sessions WHERE token_fingerprint = ? AND consumed_at IS NOT NULL",
                Integer.class, fingerprint));

        jdbc.update("UPDATE users SET enabled = TRUE WHERE id = ?", owner);
        assertTrue(rotations.rotate(parent).isPresent(),
                "denial must not burn a still-valid refresh token");
    }

    @Test
    void deletedAccountCannotRotate() {
        String parent = createActiveToken();
        String fingerprint = RefreshTokenMaterial.fingerprint(parent);
        UUID owner = jdbc.queryForObject(
                "SELECT user_id FROM refresh_sessions WHERE token_fingerprint = ?",
                UUID.class, fingerprint);
        jdbc.update("DELETE FROM users WHERE id = ?", owner);

        assertTrue(rotations.rotate(parent).isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_sessions WHERE token_fingerprint = ? AND consumed_at IS NOT NULL",
                Integer.class, fingerprint));
    }

    @Test
    void concurrentRotationsCreateExactlyOneSuccessor() throws Exception {
        String parent = createActiveToken();
        String fingerprint = RefreshTokenMaterial.fingerprint(parent);
        UUID family = jdbc.queryForObject(
                "SELECT family_id FROM refresh_sessions WHERE token_fingerprint = ?",
                UUID.class, fingerprint);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start gate timed out");
                }
                return rotations.rotate(parent).isPresent();
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_sessions WHERE family_id = ?",
                    Integer.class, family));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void successorInsertFailureRollsBackParentConsumption() {
        String parent = createActiveToken();
        RefreshSessionStore failingStore = spy(store);
        doThrow(new IllegalStateException("Simulated successor storage failure"))
                .when(failingStore).create(any(RefreshSession.class));
        RefreshSessionRotationService failingRotations = new RefreshSessionRotationService(
                failingStore, new DataSourceTransactionManager(dataSource));

        assertThrows(IllegalStateException.class, () -> failingRotations.rotate(parent));
        assertTrue(rotations.rotate(parent).isPresent(),
                "failed successor insert must leave parent eligible for retry");
    }

    @Test
    void expiredRevokedOrMissingParentsDoNotRotate() {
        String revoked = createActiveToken();
        jdbc.update("UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP " +
                "WHERE token_fingerprint = ?", RefreshTokenMaterial.fingerprint(revoked));
        assertTrue(rotations.rotate(revoked).isEmpty());

        String expired = createActiveToken();
        jdbc.update("UPDATE refresh_sessions SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes', " +
                        "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                        "WHERE token_fingerprint = ?", RefreshTokenMaterial.fingerprint(expired));
        assertTrue(rotations.rotate(expired).isEmpty());

        assertTrue(rotations.rotate(RefreshTokenMaterial.generate()).isEmpty());
        assertTrue(rotations.rotate(null).isEmpty());
    }

    @Test
    void replayOfConsumedParentRevokesItsFamilyButNotOtherAccounts() {
        String parent = createActiveToken();
        String unrelated = createActiveToken();
        String parentHash = RefreshTokenMaterial.fingerprint(parent);
        String unrelatedHash = RefreshTokenMaterial.fingerprint(unrelated);

        var successor = rotations.rotate(parent).orElseThrow();
        assertTrue(rotations.rotate(parent).isEmpty(), "old refresh token must not work twice");

        Integer revokedFamily = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_sessions WHERE family_id = " +
                        "(SELECT family_id FROM refresh_sessions WHERE token_fingerprint = ?) " +
                        "AND revoked_at IS NOT NULL",
                Integer.class, parentHash);
        assertEquals(2, revokedFamily, "both parent and successor should be revoked");

        assertTrue(rotations.rotate(successor.token()).isEmpty(),
                "revoked successor cannot refresh the session");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_sessions WHERE token_fingerprint = ? " +
                        "AND revoked_at IS NOT NULL",
                Integer.class, unrelatedHash));
        assertTrue(rotations.rotate(unrelated).isPresent(),
                "unrelated token families must stay active");
    }

    @Test
    void unknownExpiredOrAlreadyRevokedTokensDoNotRevokeOtherSessions() {
        String active = createActiveToken();
        String unknown = RefreshTokenMaterial.generate();
        assertTrue(rotations.rotate(unknown).isEmpty());

        String expired = createActiveToken();
        jdbc.update("UPDATE refresh_sessions SET consumed_at = CURRENT_TIMESTAMP, " +
                        "created_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes', " +
                        "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                        "WHERE token_fingerprint = ?", RefreshTokenMaterial.fingerprint(expired));
        assertTrue(rotations.rotate(expired).isEmpty());

        String revoked = createActiveToken();
        jdbc.update("UPDATE refresh_sessions SET consumed_at = CURRENT_TIMESTAMP, " +
                        "revoked_at = CURRENT_TIMESTAMP WHERE token_fingerprint = ?",
                RefreshTokenMaterial.fingerprint(revoked));
        assertTrue(rotations.rotate(revoked).isEmpty());

        assertTrue(rotations.rotate(active).isPresent());
    }

    @Test
    void databaseRejectsDuplicateFingerprints() {
        String token = createActiveToken();
        String fingerprint = RefreshTokenMaterial.fingerprint(token);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> store.create(new RefreshSession(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        fingerprint, Instant.now().plusSeconds(600), null)));
    }
}
