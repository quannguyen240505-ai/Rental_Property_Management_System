package com.rental.rental.service;

import java.util.UUID;

public interface PaymentVerificationClient {

    VerifiedPayment verifyReceipt(UUID receiptId);

    record VerifiedPayment(
            UUID receiptId,
            UUID reservationId,
            Long amount,
            boolean verified
    ) {
    }
}
