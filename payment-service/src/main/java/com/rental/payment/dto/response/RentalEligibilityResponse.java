package com.rental.payment.dto.response;

import java.util.UUID;
import com.rental.payment.entity.DepositReceipt;
import com.rental.payment.entity.DepositReceiptStatus;

public record RentalEligibilityResponse(UUID receiptId, String receiptReference,
        UUID holdId, UUID propertyId, UUID roomId, UUID customerId,
        long amount, DepositReceiptStatus status, boolean verified,
        boolean eligible, String reason) {
    public static RentalEligibilityResponse from(DepositReceipt receipt) {
        boolean verified = receipt.status() == DepositReceiptStatus.OWNER_VERIFIED;
        return new RentalEligibilityResponse(receipt.id(), receipt.receiptCode(),
                receipt.holdId(), receipt.propertyId(), receipt.roomId(), receipt.customerId(),
                receipt.amount(), receipt.status(), verified, verified,
                verified ? null : "RECEIPT_NOT_OWNER_VERIFIED");
    }
}
