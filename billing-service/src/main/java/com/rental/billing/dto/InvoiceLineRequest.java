package com.rental.billing.dto;

import java.math.BigDecimal;

import com.rental.billing.entity.InvoiceLineType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InvoiceLineRequest(

        @NotNull
        InvoiceLineType lineType,

        @NotBlank
        @Size(max = 255)
        String description,

        @NotNull
        @DecimalMin("0.0")
        @Digits(integer = 9, fraction = 3)
        BigDecimal quantity,

        @NotNull
        @PositiveOrZero
        Long unitPrice
) {
}