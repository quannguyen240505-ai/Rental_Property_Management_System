package com.rental.rental.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contract_deposits", uniqueConstraints = {
        @UniqueConstraint(name = "uq_contract_deposit_source_receipt", columnNames = "source_receipt_id")
})
public class ContractDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private RentalContract contract;

    @Column(name = "source_receipt_id")
    private UUID sourceReceiptId;

    @Column(name = "applied_amount", nullable = false)
    private Long appliedAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ContractDeposit() {
    }

    public UUID getId() {
        return id;
    }

    public RentalContract getContract() {
        return contract;
    }

    public void setContract(RentalContract contract) {
        this.contract = contract;
    }

    public UUID getSourceReceiptId() {
        return sourceReceiptId;
    }

    public void setSourceReceiptId(UUID sourceReceiptId) {
        this.sourceReceiptId = sourceReceiptId;
    }

    public Long getAppliedAmount() {
        return appliedAmount;
    }

    public void setAppliedAmount(Long appliedAmount) {
        this.appliedAmount = appliedAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
