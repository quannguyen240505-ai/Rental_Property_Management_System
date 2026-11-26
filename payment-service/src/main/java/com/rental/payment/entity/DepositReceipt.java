package com.rental.payment.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DepositReceipt(
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

    public DepositReceipt {
        Objects.requireNonNull(id);
        Objects.requireNonNull(propertyId);
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(holdId);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(collectedBy);
        Objects.requireNonNull(paymentMethod);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);

        if (receiptCode == null
                || receiptCode.isBlank()
                || receiptCode.length() > 50) {
            throw new IllegalArgumentException(
                    "Mã phiếu thu phải có từ 1 đến 50 ký tự");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Số tiền thu cọc phải lớn hơn 0");
        }

        if (evidenceUrl != null && evidenceUrl.length() > 500) {
            throw new IllegalArgumentException(
                    "Đường dẫn minh chứng tối đa 500 ký tự");
        }
    }
}