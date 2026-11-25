package com.rental.property.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_holds")
public class RoomHold {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viewing_id", nullable = false, unique = true)
    private ViewingAppointment viewing;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    // Optional legacy reference. Financial receipts are owned by Payment.
    @Column(name = "receipt_reference", unique = true, length = 100)
    private String receiptReference;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private RoomHoldStatus status = RoomHoldStatus.PENDING_VERIFICATION;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "verification_reference", length = 100)
    private String verificationReference;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "deposit_resolution_reference", length = 100)
    private String depositResolutionReference;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RoomHold() { }

    public RoomHold(Room room, ViewingAppointment viewing, UUID createdBy,
                    String receiptReference, LocalDateTime expiresAt) {
        this.room = room;
        this.viewing = viewing;
        this.customerId = viewing.getCustomerId();
        this.createdBy = createdBy;
        this.receiptReference = receiptReference;
        this.expiresAt = expiresAt;
    }

    public void approve(UUID ownerId, String verificationReference, LocalDateTime now) {
        this.status = RoomHoldStatus.APPROVED;
        this.approvedBy = ownerId;
        this.approvedAt = now;
        this.verificationReference = verificationReference;
    }

    public void close(RoomHoldStatus result, UUID ownerId,
                      String depositResolutionReference, LocalDateTime now) {
        this.status = result;
        this.closedBy = ownerId;
        this.closedAt = now;
        this.depositResolutionReference = depositResolutionReference;
    }

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public ViewingAppointment getViewing() { return viewing; }
    public UUID getCustomerId() { return customerId; }
    public UUID getCreatedBy() { return createdBy; }
    public String getReceiptReference() { return receiptReference; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public RoomHoldStatus getStatus() { return status; }
    public UUID getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public String getVerificationReference() { return verificationReference; }
    public UUID getClosedBy() { return closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public String getDepositResolutionReference() { return depositResolutionReference; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
