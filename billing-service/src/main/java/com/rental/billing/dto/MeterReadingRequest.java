package com.rental.billing.dto;

import com.rental.billing.entity.UtilityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MeterReadingRequest(

        @NotBlank
        @Pattern(
                regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "propertyId phải là UUID")
        String propertyId,

        @NotBlank
        @Pattern(
                regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "roomId phải là UUID")
        String roomId,

        @NotBlank
        @Pattern(
                regexp = "[0-9]{4}-(0[1-9]|1[0-2])",
                message = "billingPeriod phải có dạng yyyy-MM")
        String billingPeriod,

        @NotNull
        UtilityType utilityType,

        @NotNull
        @PositiveOrZero
        Long previousReading,

        @NotNull
        @PositiveOrZero
        Long currentReading,

        boolean meterReplaced,

        @Size(max = 500)
        String meterReplacementReason,

        @Size(max = 500)
        String evidenceUrl
) {
}