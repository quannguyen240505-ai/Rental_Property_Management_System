package com.rental.billing;

import com.rental.billing.dto.MeterReadingRequest;
import com.rental.billing.entity.UtilityType;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class MeterReadingMySqlIT extends MySqlIntegrationTestSupport {

    @Test
    void staleNextPeriodWaitsForCommitThenReturns400() throws Exception {
        readings.create(request("2026-01", 0, 100), owner);

        assertSecondWriteWaits(
                request("2026-02", 100, 150),
                request("2026-03", 100, 200), false, 400);

        assertPeriods(List.of("2026-01", "2026-02"));
        assertEquals(150L, currentReading("2026-02"));
    }

    @Test
    void validNextPeriodWaitsForCommitThenReadsNewValue() throws Exception {
        readings.create(request("2026-01", 0, 100), owner);

        assertSecondWriteWaits(
                request("2026-02", 100, 150),
                request("2026-03", 150, 200), false, 201);

        assertPeriods(List.of("2026-01", "2026-02", "2026-03"));
        assertEquals(150L, previousReading("2026-03"));
        assertEquals(200L, currentReading("2026-03"));
    }

    @Test
    void firstReadingAlsoSerializesAnEmptyMeter() throws Exception {
        assertSecondWriteWaits(
                request("2026-01", 0, 100),
                request("2026-02", 0, 150), false, 400);

        assertPeriods(List.of("2026-01"));
    }

    @Test
    void duplicatePeriodWaitsThenReturns409() throws Exception {
        assertSecondWriteWaits(
                request("2026-01", 0, 100),
                request("2026-01", 0, 120), false, 409);

        assertPeriods(List.of("2026-01"));
        assertEquals(100L, currentReading("2026-01"));
    }

    @Test
    void rollbackReleasesLockAndAllowsSecondWrite() throws Exception {
        assertSecondWriteWaits(
                request("2026-01", 0, 100),
                request("2026-01", 0, 120), true, 201);

        assertPeriods(List.of("2026-01"));
        assertEquals(120L, currentReading("2026-01"));
    }

    @Test
    void waterDoesNotWaitForElectricityOnSameRoom() throws Exception {
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);

        Future<Integer> first = workers.submit(() -> newTransaction().execute(state -> {
            readings.create(request("2026-01", 0, 100), owner);
            firstSaved.countDown();
            awaitSignal(allowCommit);
            return 201;
        }));

        try {
            awaitSignal(firstSaved);
            Future<Integer> water = workers.submit(() -> {
                readings.create(new MeterReadingRequest(
                        propertyId, roomId, "2026-01", UtilityType.WATER,
                        0L, 10L, false, null, null), owner);
                return 201;
            });
            assertEquals(201, water.get(5, TimeUnit.SECONDS).intValue());
        } finally {
            allowCommit.countDown();
        }

        assertEquals(201, first.get(15, TimeUnit.SECONDS).intValue());
        assertEquals(2L, count(
                "SELECT COUNT(*) FROM utility_readings WHERE room_id = ?", roomId));
    }

    private void assertSecondWriteWaits(
            MeterReadingRequest firstRequest,
            MeterReadingRequest secondRequest,
            boolean rollbackFirst,
            int expectedSecondStatus) throws Exception {

        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch allowFirstCompletion = new CountDownLatch(1);
        CountDownLatch secondEnteredTransaction = new CountDownLatch(1);

        Future<Integer> first = workers.submit(() -> newTransaction().execute(state -> {
            readings.create(firstRequest, owner);
            firstSaved.countDown();
            awaitSignal(allowFirstCompletion);
            if (rollbackFirst) {
                state.setRollbackOnly();
            }
            return 201;
        }));

        Future<Integer> second = null;
        try {
            awaitSignal(firstSaved);
            second = workers.submit(() -> {
                try {
                    return newTransaction().execute(state -> {
                        secondEnteredTransaction.countDown();
                        readings.create(secondRequest, owner);
                        return 201;
                    });
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            });

            awaitSignal(secondEnteredTransaction);
            Future<Integer> waitingWrite = second;
            assertThrows(TimeoutException.class,
                    () -> waitingWrite.get(1, TimeUnit.SECONDS),
                    "Second write must wait while the first transaction is uncommitted");
        } finally {
            allowFirstCompletion.countDown();
        }

        assertEquals(201, first.get(15, TimeUnit.SECONDS).intValue());
        assertNotNull(second);
        assertEquals(expectedSecondStatus,
                second.get(15, TimeUnit.SECONDS).intValue());
    }

    private MeterReadingRequest request(String period, long previous, long current) {
        return new MeterReadingRequest(
                propertyId, roomId, period, UtilityType.ELECTRICITY,
                previous, current, false, null, null);
    }

    private void assertPeriods(List<String> expected) {
        assertEquals(expected, jdbc.queryForList("""
                SELECT billing_period FROM utility_readings
                WHERE room_id = ? AND utility_type = 'ELECTRICITY'
                ORDER BY billing_period
                """, String.class, roomId));
    }

    private long previousReading(String period) {
        return java.util.Objects.requireNonNull(jdbc.queryForObject("""
                SELECT previous_reading FROM utility_readings
                WHERE room_id = ? AND billing_period = ?
                AND utility_type = 'ELECTRICITY'
                """, Long.class, roomId, period));
    }

    private long currentReading(String period) {
        return java.util.Objects.requireNonNull(jdbc.queryForObject("""
                SELECT current_reading FROM utility_readings
                WHERE room_id = ? AND billing_period = ?
                AND utility_type = 'ELECTRICITY'
                """, Long.class, roomId, period));
    }
}
