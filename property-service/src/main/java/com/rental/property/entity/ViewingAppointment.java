package com.rental.property.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "viewing_appointments", indexes = {
        @Index(name = "idx_viewings_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_viewings_room_time", columnList = "room_id, start_at, end_at"),
        @Index(name = "idx_viewings_staff_time", columnList = "staff_id, start_at, end_at"),
        @Index(name = "idx_viewings_status", columnList = "status, start_at")
})
public class ViewingAppointment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // Auth Service owns accounts; we store references, not a cross-service foreign key.
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private ViewingStatus status = ViewingStatus.REQUESTED;

    @Column(name = "result_notes", length = 1000)
    private String resultNotes;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ViewingAppointment() { }

    public ViewingAppointment(Room room, UUID customerId, LocalDateTime startAt, LocalDateTime endAt) {
        this.room = room;
        this.customerId = customerId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = ViewingStatus.REQUESTED;
    }

    public void claim(UUID userId) {
        this.staffId = userId;
        this.updatedBy = userId;
        this.status = ViewingStatus.CONFIRMED;
    }

    public void finish(ViewingStatus result, String notes, UUID actorId) {
        this.status = result;
        this.resultNotes = notes;
        this.updatedBy = actorId;
    }

    public void cancel(UUID customerId) {
        this.status = ViewingStatus.CANCELLED;
        this.updatedBy = customerId;
    }

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public UUID getCustomerId() { return customerId; }
    public UUID getStaffId() { return staffId; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public ViewingStatus getStatus() { return status; }
    public String getResultNotes() { return resultNotes; }
    public UUID getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
