package com.rental.rental.service;

import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.access.RentalContractAccessService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
class ContractActivationConcurrencyIT {

    @Autowired
    private ContractActivationService activationService;

    @MockitoBean
    private RentalContractAccessService contractAccessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID roomId;
    private UUID tenantId;
    private UUID contractA;
    private UUID contractB;

    private byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    @BeforeEach
    void setUp() {
        String database = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class
        );

        assertEquals("rental_test", database,
                "Integration Test must use rental_test");

        roomId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        contractA = UUID.randomUUID();
        contractB = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO tenant_profiles (id, full_name)
                VALUES (?, ?)
                """,
                bytes(tenantId),
                "Integration Test Tenant"
        );

        insertContract(contractA);
        insertContract(contractB);
    }

    private void insertContract(UUID contractId) {
        jdbcTemplate.update(
                """
                INSERT INTO rental_contracts
                (id, room_id, tenant_profile_id, status,
                 start_date, end_date, rent_amount,
                 deposit_required, version)
                VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?, 0)
                """,
                bytes(contractId),
                bytes(roomId),
                bytes(tenantId),
                Date.valueOf(LocalDate.of(2027, 1, 1)),
                Date.valueOf(LocalDate.of(2027, 2, 1)),
                5_000_000L,
                1_000_000L
        );

        jdbcTemplate.update(
                """
                INSERT INTO contract_deposits
                (id, contract_id, applied_amount)
                VALUES (?, ?, ?)
                """,
                bytes(UUID.randomUUID()),
                bytes(contractId),
                1_000_000L
        );
    }

    @Test
    void onlyOneOverlappingContractCanBeActivated()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return tryActivate(contractA);
            });

            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return tryActivate(contractB);
            });

            start.countDown();

            boolean resultA =
                    first.get(30, TimeUnit.SECONDS);

            boolean resultB =
                    second.get(30, TimeUnit.SECONDS);

            assertNotEquals(resultA, resultB,
                    "Exactly one activation must succeed");

            Integer activeCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM rental_contracts
                            WHERE room_id = ?
                              AND status = 'ACTIVE'
                            """,
                            Integer.class,
                            bytes(roomId)
                    );

            assertEquals(1, activeCount);

        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryActivate(UUID contractId) {
        try {
            activationService.activate(contractId);
            return true;
        } catch (ContractConflictException ex) {
            return false;
        }
    }

    @AfterEach
    void cleanUp() {
        if (contractA == null || contractB == null) {
            return;
        }

        jdbcTemplate.update(
                """
                DELETE FROM contract_deposits
                WHERE contract_id IN (?, ?)
                """,
                bytes(contractA),
                bytes(contractB)
        );

        jdbcTemplate.update(
                """
                DELETE FROM rental_contracts
                WHERE id IN (?, ?)
                """,
                bytes(contractA),
                bytes(contractB)
        );

        jdbcTemplate.update(
                "DELETE FROM room_activation_locks WHERE room_id = ?",
                bytes(roomId)
        );

        jdbcTemplate.update(
                "DELETE FROM tenant_profiles WHERE id = ?",
                bytes(tenantId)
        );
    }
}
