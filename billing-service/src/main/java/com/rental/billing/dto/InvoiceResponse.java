package com.rental.billing.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.rental.billing.entity.Invoice;
import com.rental.billing.entity.InvoiceLine;
import com.rental.billing.entity.InvoiceStatus;

public record InvoiceResponse(
        String id,
        String propertyId,
        String roomId,
        String contractId,
        String tenantUserId,
        String billingPeriod,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        Long totalAmount,
        Long paidAmount,
        Long outstandingAmount,
        String createdBy,
        LocalDateTime createdAt,
        List<InvoiceLineResponse> lines
) {

    public static InvoiceResponse fromEntity(
            Invoice invoice,
            List<InvoiceLine> lines) {

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getPropertyId(),
                invoice.getRoomId(),
                invoice.getContractId(),
                invoice.getTenantUserId(),
                invoice.getBillingPeriod(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getTotalAmount() - invoice.getPaidAmount(),
                invoice.getCreatedBy(),
                invoice.getCreatedAt(),
                lines.stream()
                        .map(InvoiceLineResponse::fromEntity)
                        .toList());
    }
}