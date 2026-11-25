package com.rental.rental.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RentalContractResponse(

        UUID id,
        UUID roomId,
        UUID tenantProfileId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long rentAmount,
        Long depositRequired

) {
}
