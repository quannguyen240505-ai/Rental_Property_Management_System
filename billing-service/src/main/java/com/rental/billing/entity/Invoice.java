package com.rental.billing.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(
    name = "invoices",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_invoice_contract_period",
        columnNames = {"contract_id", "billing_period"}
    )
)
public class Invoice {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "contract_id", nullable = false, length = 36)
    private String contractId;

    @Column(name = "tenant_user_id", nullable = false, length = 36)
    private String tenantUserId;

    @Column(name = "billing_period", nullable = false,
            columnDefinition = "CHAR(7)")
    private String billingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount = 0L;

    @Column(name = "paid_amount", nullable = false)
    private Long paidAmount = 0L;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Invoice() {
    }

    public Invoice(
            String propertyId,
            String roomId,
            String contractId,
            String tenantUserId,
            String billingPeriod,
            LocalDate dueDate,
            Long totalAmount,
            String createdBy) {

        if (totalAmount == null || totalAmount < 0) {
            throw new IllegalArgumentException(
                    "Tổng tiền phải là số không âm");
        }

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.roomId = roomId;
        this.contractId = contractId;
        this.tenantUserId = tenantUserId;
        this.billingPeriod = billingPeriod;
        this.dueDate = dueDate;
        this.totalAmount = totalAmount;
        this.createdBy = createdBy;
    }
    public void issue(LocalDate date) {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException(
                    "Chỉ được phát hành hóa đơn DRAFT");
        }

        if (date == null) {
            throw new IllegalArgumentException(
                    "Ngày phát hành không được để trống");
        }

        if (dueDate == null || dueDate.isBefore(date)) {
            throw new IllegalArgumentException(
                    "Hạn thanh toán phải từ ngày phát hành trở đi");
        }

        this.status = InvoiceStatus.ISSUED;
        this.issueDate = date;
    }

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getContractId() {
        return contractId;
    }

    public String getTenantUserId() {
        return tenantUserId;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getPaidAmount() {
        return paidAmount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}