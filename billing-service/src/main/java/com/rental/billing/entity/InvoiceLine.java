package com.rental.billing.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false)
    private InvoiceLineType lineType;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price")
    private Long unitPrice;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InvoiceLine() {
    }

    public InvoiceLine(
            Invoice invoice,
            InvoiceLineType lineType,
            String description,
            BigDecimal quantity,
            Long unitPrice) {

        if (invoice == null || lineType == null) {
            throw new IllegalArgumentException(
                    "Phải có hóa đơn và loại khoản thu");
        }

        if (description == null || description.isBlank()
                || description.length() > 255) {
            throw new IllegalArgumentException(
                    "Mô tả phải có từ 1 đến 255 ký tự");
        }

        if (quantity == null || quantity.signum() < 0
                || unitPrice == null || unitPrice < 0) {
            throw new IllegalArgumentException(
                    "Số lượng và đơn giá phải không âm");
        }

        BigDecimal normalizedQuantity;
        long calculatedAmount;

        try {
            normalizedQuantity = quantity.setScale(
                    3, java.math.RoundingMode.UNNECESSARY);

            if (normalizedQuantity.precision() > 12) {
                throw new IllegalArgumentException(
                        "Số lượng vượt giới hạn lưu trữ");
            }

            calculatedAmount = normalizedQuantity
                    .multiply(BigDecimal.valueOf(unitPrice))
                    .longValueExact();

        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Số lượng tối đa 3 chữ số thập phân; "
                    + "thành tiền phải là số nguyên VND "
                    + "và nằm trong giới hạn Long",
                    ex);
        }

        this.id = UUID.randomUUID().toString();
        this.invoice = invoice;
        this.lineType = lineType;
        this.description = description.trim();
        this.quantity = normalizedQuantity;
        this.unitPrice = unitPrice;
        this.amount = calculatedAmount;
    }

    @PrePersist
    void beforeInsert() {
        createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public InvoiceLineType getLineType() {
        return lineType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Long getUnitPrice() {
        return unitPrice;
    }

    public Long getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}