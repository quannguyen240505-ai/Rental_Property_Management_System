package com.rental.billing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.rental.billing.dto.MeterReadingRequest;
import com.rental.billing.entity.UtilityType;
import com.rental.billing.service.MeterReadingPolicy;

class MeterReadingPolicyTest {

    private MeterReadingRequest request(
            long previous,
            long current,
            boolean replaced,
            String reason) {

        return new MeterReadingRequest(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                "2026-11",
                UtilityType.ELECTRICITY,
                previous,
                current,
                replaced,
                reason,
                null);
    }

    @Test
    void acceptsContinuousReading() {
        assertDoesNotThrow(() ->
                MeterReadingPolicy.validate(
                        request(150, 200, false, null), 150L));
    }

    @Test
    void rejectsBrokenContinuity() {
        var error = assertThrows(ResponseStatusException.class, () ->
                MeterReadingPolicy.validate(
                        request(140, 200, false, null), 150L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsDecreasingReading() {
        assertThrows(ResponseStatusException.class, () ->
                MeterReadingPolicy.validate(
                        request(150, 100, false, null), 150L));
    }

    @Test
    void acceptsReplacementWithReason() {
        assertDoesNotThrow(() ->
                MeterReadingPolicy.validate(
                        request(0, 20, true, "Thay công tơ hỏng"), 150L));
    }

    @Test
    void rejectsReplacementWithoutReason() {
        assertThrows(ResponseStatusException.class, () ->
                MeterReadingPolicy.validate(
                        request(0, 20, true, " "), 150L));
    }

    @Test
    void rejectsNegativeReading() {
        assertThrows(ResponseStatusException.class, () ->
                MeterReadingPolicy.validate(
                        request(-1, 20, false, null), null));
    }
}