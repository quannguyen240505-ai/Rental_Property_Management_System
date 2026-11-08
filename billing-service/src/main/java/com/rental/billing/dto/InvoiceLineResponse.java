package com.rental.billing.dto;

import java.math.BigDecimal;

import com.rental.billing.entity.InvoiceLine;
import com.rental.billing.entity.InvoiceLineType;

public record InvoiceLineResponse(
        String id,
        InvoiceLineType lineType,
        String description,
        BigDecimal quantity,
        Long unitPrice,
        Long amount
) {

    public static InvoiceLineResponse fromEntity(
            InvoiceLine line) {

        return new InvoiceLineResponse(
                line.getId(),
                line.getLineType(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getAmount());
    }
}