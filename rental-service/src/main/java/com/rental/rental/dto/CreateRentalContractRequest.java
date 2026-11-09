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

        @NotNull @PositiveOrZero Long depositRequired

) {
}
