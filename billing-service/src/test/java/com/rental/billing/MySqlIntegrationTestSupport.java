package com.rental.billing;

import com.rental.billing.service.InvoiceService;
import com.rental.billing.service.MeterReadingService;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = BillingServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.datasource.url=${BILLING_IT_DB_URL}",
            "spring.datasource.username=${MYSQL_USER}",
            "spring.datasource.password=${MYSQL_PASSWORD}",
            "auth.service-key=test-only-internal-service-key-1234567890",
            "eureka.client.enabled=false"
        })
@ActiveProfiles("mysql-it")
@ContextConfiguration(initializers = MySqlIntegrationTestSupport.DatabaseGuard.class)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
abstract class MySqlIntegrationTestSupport {

    protected JdbcTemplate jdbc;
    @Autowired protected DataSource dataSource;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected MeterReadingService readings;
    @Autowired protected InvoiceService invoices;

    protected String propertyId;
    protected String roomId;
    protected Authentication owner;
    protected ExecutorService workers;

    @BeforeEach
    void prepareFixture() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
        }
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class)
                .startsWith("rental_billing_it_"));

        propertyId = UUID.randomUUID().toString();
        roomId = UUID.randomUUID().toString();
        owner = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        workers = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cleanupFixture() throws Exception {
        if (workers != null) {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS),
                    "Worker transaction did not terminate; cleanup stopped");
        }
        if (propertyId != null) {
            // Delete only this test's UUID fixture, never truncate the database.
            jdbc.update("""
                    DELETE l FROM invoice_lines l
                    JOIN invoices i ON i.id = l.invoice_id
                    WHERE i.property_id = ?
                    """, propertyId);
            jdbc.update("DELETE FROM invoices WHERE property_id = ?", propertyId);
            jdbc.update("DELETE FROM utility_readings WHERE property_id = ?", propertyId);
            jdbc.update("DELETE FROM utility_meter_write_locks WHERE room_id = ?", roomId);
        }
    }

    protected TransactionTemplate newTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(20);
        return transaction;
    }

    protected static void awaitSignal(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test transaction interrupted", exception);
        }
    }

    protected long count(String sql, Object... parameters) {
        Long value = jdbc.queryForObject(sql, Long.class, parameters);
        return java.util.Objects.requireNonNull(value);
    }

    public static class DatabaseGuard
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = context.getEnvironment()
                    .getRequiredProperty("spring.datasource.url");
            if (!url.matches("^jdbc:mysql://(localhost|127\\.0\\.0\\.1)"
                    + "(:[0-9]+)?/rental_billing_it_[a-z0-9_]+(\\?.*)?$")) {
                throw new IllegalStateException(
                        "Integration tests require a LOCAL database named rental_billing_it_..."
                        + " supplied through BILLING_IT_DB_URL");
            }
        }
    }
}
