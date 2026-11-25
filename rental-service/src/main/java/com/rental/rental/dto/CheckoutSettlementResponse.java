package com.rental.rental.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CheckoutSettlementResponse(
        UUID id,
        UUID contractId,
        String contractStatus,
        LocalDate checkoutDate,
        Long appliedDeposit,
        Long deductionAmount,
        Long refundDue,
        String notes
) {}
