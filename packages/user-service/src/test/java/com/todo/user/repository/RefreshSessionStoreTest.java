package com.todo.user.repository;

import com.todo.user.security.RefreshSession;
import com.todo.user.security.RefreshTokenMaterial;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
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

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        store = new RefreshSessionStore(jdbc);
    }

    private static String createActiveToken() {
        String token = RefreshTokenMaterial.generate();
        store.create(new RefreshSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
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
        jdbc.update("UPDATE refresh_sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                        "WHERE token_fingerprint = ?", RefreshTokenMaterial.fingerprint(expired));
        assertFalse(store.consume(expired));

        String revoked = createActiveToken();
        jdbc.update("UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP " +
                        "WHERE token_fingerprint = ?", RefreshTokenMaterial.fingerprint(revoked));
        assertFalse(store.consume(revoked));
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
