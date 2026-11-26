package com.rental.payment.dto.request;

import java.util.UUID;

import com.rental.payment.entity.DepositPaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateDepositReceiptRequest(

        @NotNull
        UUID holdId,

        @NotNull
        @Positive
        Long amount,

        @NotNull
        DepositPaymentMethod paymentMethod,

        @Size(max = 500)
        String evidenceUrl
) {
}