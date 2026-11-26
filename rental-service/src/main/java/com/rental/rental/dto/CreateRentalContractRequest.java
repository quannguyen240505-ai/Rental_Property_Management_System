package com.rental.rental.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record CreateRentalContractRequest(

        @NotNull UUID roomId,

        @NotNull UUID tenantProfileId,

        @NotNull LocalDate startDate,

        @NotNull LocalDate endDate,

        @NotNull @PositiveOrZero Long rentAmount,

        @NotNull @PositiveOrZero Long depositRequired,

        UUID holdId

) {
    public CreateRentalContractRequest(
            UUID roomId,
            UUID tenantProfileId,
            LocalDate startDate,
            LocalDate endDate,
            Long rentAmount,
            Long depositRequired
    ) {
        this(
                roomId,
                tenantProfileId,
                startDate,
                endDate,
                rentAmount,
                depositRequired,
                null
        );
    }
}
