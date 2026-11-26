package com.rental.payment.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.rental.payment.entity.DepositPaymentMethod;
import com.rental.payment.entity.DepositReceipt;
import com.rental.payment.entity.DepositReceiptStatus;

public record DepositReceiptResponse(
        UUID id,
        String receiptCode,
        UUID propertyId,
        UUID roomId,
        UUID holdId,
        UUID customerId,
        UUID collectedBy,
        long amount,
        DepositPaymentMethod paymentMethod,
        DepositReceiptStatus status,
        String evidenceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    @com.fasterxml.jackson.annotation.JsonProperty("receiptReference")
    public String receiptReference() {
        return receiptCode;
    }

    public static DepositReceiptResponse from(DepositReceipt receipt) {
        return new DepositReceiptResponse(
                receipt.id(),
                receipt.receiptCode(),
                receipt.propertyId(),
                receipt.roomId(),
                receipt.holdId(),
                receipt.customerId(),
                receipt.collectedBy(),
                receipt.amount(),
                receipt.paymentMethod(),
                receipt.status(),
                receipt.evidenceUrl(),
                receipt.createdAt(),
                receipt.updatedAt());
    }
}
