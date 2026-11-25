package com.rental.rental.service;

import com.rental.rental.dto.CreateRentalContractRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RentalContractValidationTest {

    private final ValidatorFactory factory =
            Validation.buildDefaultValidatorFactory();

    private final Validator validator = factory.getValidator();

    private CreateRentalContractRequest request(
            UUID roomId,
            Long rentAmount,
            Long depositRequired
    ) {
        return new CreateRentalContractRequest(
                roomId,
                UUID.randomUUID(),
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 12, 1),
                rentAmount,
                depositRequired
        );
    }

    @Test
    void negativeRentAmount_invalid() {
        var violations = validator.validate(
                request(UUID.randomUUID(), -1000L, 2000L)
        );

        assertTrue(violations.stream().anyMatch(
                v -> v.getPropertyPath().toString()
                        .equals("rentAmount")
        ));
    }

    @Test
    void negativeDeposit_invalid() {
        var violations = validator.validate(
                request(UUID.randomUUID(), 1000L, -2000L)
        );

        assertTrue(violations.stream().anyMatch(
                v -> v.getPropertyPath().toString()
                        .equals("depositRequired")
        ));
    }

    @Test
    void missingRoomId_invalid() {
        var violations = validator.validate(
                request(null, 1000L, 2000L)
        );

        assertTrue(violations.stream().anyMatch(
                v -> v.getPropertyPath().toString()
                        .equals("roomId")
        ));
    }

    @Test
    void validRequest_noViolations() {
        var violations = validator.validate(
                request(UUID.randomUUID(), 1000L, 2000L)
        );

        assertTrue(violations.isEmpty());
    }
}
