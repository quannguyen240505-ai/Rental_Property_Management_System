package com.rental.billing.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvoiceRequest(

        @NotNull
        UUID propertyId,

        @NotNull
        UUID roomId,

        @NotNull
        UUID contractId,

        @NotNull
        UUID tenantUserId,

        @NotBlank
        @Pattern(
                regexp = "[0-9]{4}-(0[1-9]|1[0-2])",
                message = "billingPeriod phải có dạng yyyy-MM")
        String billingPeriod,

        @NotNull
        LocalDate dueDate,

        @NotNull
        @Size(min = 1, max = 100)
        List<@NotNull @Valid InvoiceLineRequest> lines
) {
}