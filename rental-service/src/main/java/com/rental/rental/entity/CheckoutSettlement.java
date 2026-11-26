package com.rental.rental.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkout_settlements", uniqueConstraints = {
        @UniqueConstraint(name = "uq_checkout_contract", columnNames = "contract_id")
})
public class CheckoutSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private RentalContract contract;

    @Column(name = "checkout_date", nullable = false)
    private LocalDate checkoutDate;

    @Column(name = "applied_deposit", nullable = false)
    private Long appliedDeposit;

    @Column(name = "deduction_amount", nullable = false)
    private Long deductionAmount;

    @Column(name = "refund_due", nullable = false)
    private Long refundDue;

    @Column(name = "notes", length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public RentalContract getContract() { return contract; }
    public void setContract(RentalContract contract) { this.contract = contract; }
    public LocalDate getCheckoutDate() { return checkoutDate; }
    public void setCheckoutDate(LocalDate checkoutDate) { this.checkoutDate = checkoutDate; }
    public Long getAppliedDeposit() { return appliedDeposit; }
    public void setAppliedDeposit(Long appliedDeposit) { this.appliedDeposit = appliedDeposit; }
    public Long getDeductionAmount() { return deductionAmount; }
    public void setDeductionAmount(Long deductionAmount) { this.deductionAmount = deductionAmount; }
    public Long getRefundDue() { return refundDue; }
    public void setRefundDue(Long refundDue) { this.refundDue = refundDue; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
