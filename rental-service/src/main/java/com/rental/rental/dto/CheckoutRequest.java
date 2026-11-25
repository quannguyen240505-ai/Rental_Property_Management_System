package com.rental.rental.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record CheckoutRequest(
        @NotNull LocalDate checkoutDate,
        @NotNull @PositiveOrZero Long deductionAmount,
        String notes
) {}
