package com.rental.property.service;

import com.rental.property.dto.request.CreatePropertyRequest;
import com.rental.property.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropertyServiceValidationTest {

    private final PropertyRepository repository =
            mock(PropertyRepository.class);

    private final PropertyService service =
            new PropertyService(repository);

    private void expectBadRequest(
            String name, String address, String phone) {

        CreatePropertyRequest request =
                new CreatePropertyRequest(
                        name, address, null, phone);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createProperty(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void nameTooLong_returns400() {
        expectBadRequest("A".repeat(151), "Quy Nhon", null);
    }

    @Test
    void addressTooLong_returns400() {
        expectBadRequest("Khu tro A", "B".repeat(256), null);
    }

    @Test
    void phoneTooLong_returns400() {
        expectBadRequest("Khu tro A", "Quy Nhon", "1".repeat(21));
    }

    @Test
    void blankName_returns400() {
        expectBadRequest("   ", "Quy Nhon", null);
    }
}
