package com.adsyahir.invoice_hub_backend.support;

import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.BDDMockito.given;

/**
 * Base class for integration tests. Boots the full Spring context against a real
 * PostgreSQL running in Docker, with the project's real Flyway migrations applied
 * — the same engine and the same schema as production.
 *
 * <p>The container is a singleton: it is started once in a static initializer and
 * shared by every test class in the run (starting an already-started container is
 * a no-op). Testcontainers' Ryuk sidecar removes it when the JVM exits, so there
 * is nothing to tear down.
 *
 * <p>We wire it in with {@link DynamicPropertySource} rather than the
 * {@code @Testcontainers} / {@code @Container} JUnit extension: Spring Boot 4 runs
 * on JUnit 6, and that extension still targets JUnit 5.
 *
 * <p>Tests are NOT wrapped in a rolled-back transaction. The services under test
 * write audit rows and notifications with {@code REQUIRES_NEW}, which commit
 * independently — a test-managed rollback would not undo them and would give a
 * false picture of what the code actually persists. Instead every test starts from
 * a truncated database.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("invoice_hub_test")
                    .withUsername("test")
                    .withPassword("test");

    /**
     * A real broker, so the async flow is genuinely exercised: the relay publishes on commit,
     * the record round-trips through Kafka, and a consumer picks it up. A mocked KafkaTemplate
     * would assert that we CALLED send() — not that anything was ever delivered or consumed.
     */
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    /** Real Redis, so @Cacheable/@CacheEvict and the rate-limit INCR are genuinely exercised. */
    private static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getFirstMappedPort());
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestFixtures fixtures;

    @Autowired
    protected StringRedisTemplate redis;

    /** Clear the cache + rate-limit counters between tests so nothing leaks across them. */
    @BeforeEach
    void flushRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    /**
     * No real SMTP in tests. Without this, InvoiceEmailConsumer would fail against a dead
     * port on every send, burn its retries and dump records into the DLT — noise that has
     * nothing to do with what is under test. Mocking the sender also lets a test assert
     * that an email was actually dispatched.
     */
    @MockitoBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void stubMailSender() {
        // A mock returns null by default, which would NPE inside MimeMessageHelper.
        given(mailSender.createMimeMessage())
                .willAnswer(invocation -> new MimeMessage((Session) null));
    }

    /**
     * Wipe all mutable data between tests. TRUNCATE (not delete) because the entities use
     * {@code @SQLDelete} soft-deletes — a JPA delete would only set deleted_at and leave the row
     * behind, so ids and unique constraints (e.g. the globally-unique invoice_number) would carry
     * across tests.
     *
     * <p>Geo reference tables (states/cities/postcodes) are left alone: they are seed data, not
     * test state.
     *
     * <p>RETRIED, because the consumers are asynchronous. A test can finish while its Kafka
     * consumer is still committing (writing the e-invoice result, an audit row, a notification).
     * That consumer's transaction holds row locks; TRUNCATE wants an ACCESS EXCLUSIVE lock on the
     * same tables, and the two deadlock — surfacing as PessimisticLockingFailureException in a
     * test that has nothing to do with Kafka. Consumers finish in well under a second, so a short
     * retry is enough. Stopping the listener containers between tests would be the heavier
     * alternative, at the cost of not exercising the real consumer lifecycle.
     */
    @BeforeEach
    void resetDatabase() {
        DataAccessException lastFailure = null;
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                truncateAll();
                return;
            } catch (DataAccessException e) {
                lastFailure = e;   // an in-flight consumer still holds locks — let it drain
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IllegalStateException(
                "Could not reset the database — a consumer transaction never released its locks",
                lastFailure);
    }

    private void truncateAll() {
        jdbc.execute("""
                TRUNCATE TABLE
                    payments,
                    invoice_line_items,
                    invoices,
                    clients,
                    notifications,
                    audit_logs,
                    tenant_einvoice_settings,
                    password_reset_tokens,
                    refresh_tokens,
                    team_invitations,
                    users,
                    tenants,
                    role_permissions,
                    roles,
                    permissions
                RESTART IDENTITY CASCADE
                """);
    }
}
