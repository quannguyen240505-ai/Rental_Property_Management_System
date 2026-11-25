package com.rental.property.service;

import com.rental.property.dto.request.CreateRoomRequest;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceValidationTest {

    private final RoomRepository rooms =
            mock(RoomRepository.class);

    private final PropertyRepository properties =
            mock(PropertyRepository.class);

    private final RoomService service =
            new RoomService(rooms, properties);

    private void expectBadRequest(
            String code, String title, BigDecimal area) {

        CreateRoomRequest request = new CreateRoomRequest(
                UUID.randomUUID(),
                code,
                title,
                null,
                1,
                area,
                2000000L
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createRoom(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(rooms, properties);
    }

    @Test
    void codeTooLong_returns400() {
        expectBadRequest(
                "A".repeat(31), "Phong A101",
                new BigDecimal("25.50"));
    }

    @Test
    void titleTooLong_returns400() {
        expectBadRequest(
                "A101", "B".repeat(151),
                new BigDecimal("25.50"));
    }

    @Test
    void areaExceedsPrecision_returns400() {
        expectBadRequest(
                "A101", "Phong A101",
                new BigDecimal("1000000.00"));
    }

    @Test
    void areaExceedsScale_returns400() {
        expectBadRequest(
                "A101", "Phong A101",
                new BigDecimal("25.123"));
    }
}
