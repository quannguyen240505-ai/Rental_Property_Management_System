package com.rental.payment;

import com.rental.payment.dto.request.CreateDepositReceiptRequest;
import com.rental.payment.entity.DepositPaymentMethod;
import com.rental.payment.integration.DepositHoldVerifier;
import com.rental.payment.integration.VerifiedDepositHold;
import com.rental.payment.repository.DepositReceiptRepository;
import com.rental.payment.service.DepositReceiptService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit opt-in: -Dtest=DepositReceiptMySqlIT test. Never points at the app database by default. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DepositReceiptMySqlIT {
    private static DataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private DepositReceiptService service;
    private DepositHoldVerifier verifier;
    private UUID hold, user;
    private Authentication staff;

    @BeforeAll void initialize() {
        String url = required("PAYMENT_IT_DB_URL");
        if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/rental_payment_it_[A-Za-z0-9_]+(\\?.*)?")) {
            throw new IllegalArgumentException("PAYMENT_IT_DB_URL must point at a local rental_payment_it_* schema");
        }
        source = new DriverManagerDataSource(url, required("PAYMENT_IT_DB_USER"), required("PAYMENT_IT_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.startsWith("rental_payment_it_"));
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .baselineOnMigrate(false).cleanDisabled(true).load().migrate();
        context = new AnnotationConfigApplicationContext(TxConfig.class);
        service = context.getBean(DepositReceiptService.class);
        verifier = context.getBean(DepositHoldVerifier.class);
        assertTrue(AopUtils.isAopProxy(service), "Calls must use the real Spring transaction proxy");
    }

    @BeforeEach void setUp() {
        reset(verifier);
        hold = UUID.randomUUID(); user = UUID.randomUUID();
        staff = new UsernamePasswordAuthenticationToken(user.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        when(verifier.verifyForCollection(eq(hold), any())).thenReturn(validHold(true, null));
    }

    @Test void sameHoldSamePayloadConcurrentReturnsOneReceipt() throws Exception {
        var results = concurrently(() -> service.create(request(500000L), staff).id().toString(),
                () -> service.create(request(500000L), staff).id().toString());
        assertEquals(results.get(0), results.get(1));
        assertEquals(1L, countReceipts());
        assertEquals(500000L, jdbc.queryForObject("SELECT SUM(amount) FROM deposit_receipts WHERE hold_id = ?", Long.class, hold.toString()));
    }

    @Test void sameHoldChangedAmountConcurrentReturnsBusinessConflict() throws Exception {
        var results = concurrently(() -> tryCreate(500000L), () -> tryCreate(600000L));
        assertEquals(1, results.stream().filter("CREATED"::equals).count());
        assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
        assertEquals(1L, countReceipts());
    }

    @Test void expiredHoldRollsBackReceiptAndNewLockRow() {
        var h = validHold(true, null);
        when(verifier.verifyForCollection(eq(hold), any())).thenReturn(new VerifiedDepositHold(
                h.holdId(), h.propertyId(), h.roomId(), h.customerId(), h.holdStatus(), h.roomStatus(),
                OffsetDateTime.now().minusSeconds(1), true, null));
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(request(500000L), staff));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(0L, countReceipts());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM deposit_receipt_hold_locks WHERE hold_id = ?", Long.class, hold.toString()));
    }

    @Test void replayAfterReceiptRecordedReturnsOriginalIdAndDoesNotWriteAgain() {
        var first = service.create(request(500000L), staff);
        when(verifier.verifyForCollection(eq(hold), any())).thenReturn(validHold(false, "RECEIPT_ALREADY_EXISTS"));
        var second = service.create(request(500000L), staff);
        assertEquals(first.id(), second.id());
        assertEquals(1L, countReceipts());
    }

    private List<String> concurrently(Callable<String> a, Callable<String> b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return a.call(); });
            var second = executor.submit(() -> { start.await(); return b.call(); });
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    private String tryCreate(long amount) {
        try { service.create(request(amount), staff); return "CREATED"; }
        catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 409) throw ex;
            return "CONFLICT";
        }
    }
    private VerifiedDepositHold validHold(boolean eligible, String reason) {
        // Stable authoritative identifiers across retries; only the eligibility changes.
        return new VerifiedDepositHold(hold,
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                "PENDING_VERIFICATION", "RESERVED_PENDING", OffsetDateTime.now().plusHours(1), eligible, reason);
    }
    private CreateDepositReceiptRequest request(long amount) {
        return new CreateDepositReceiptRequest(hold, amount, DepositPaymentMethod.CASH, null);
    }
    private Long countReceipts() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM deposit_receipts WHERE hold_id = ?", Long.class, hold.toString());
    }
    @AfterEach void cleanOwnFixtures() {
        if (hold != null && jdbc != null) {
            jdbc.update("DELETE FROM deposit_receipts WHERE hold_id = ?", hold.toString());
            jdbc.update("DELETE FROM deposit_receipt_hold_locks WHERE hold_id = ?", hold.toString());
        }
    }
    @AfterAll void close() { if (context != null) context.close(); }
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " for explicit MySQL IT execution");
        return value;
    }
    @Configuration
    @EnableTransactionManagement
    static class TxConfig {
        @Bean DataSource dataSource() { return source; }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean DepositReceiptRepository repository(DataSource ds) { return new DepositReceiptRepository(new JdbcTemplate(ds)); }
        @Bean DepositHoldVerifier verifier() { return mock(DepositHoldVerifier.class); }
        @Bean DepositReceiptService service(DepositReceiptRepository repo, DepositHoldVerifier verifier) {
            return new DepositReceiptService(repo, verifier);
        }
    }
}
