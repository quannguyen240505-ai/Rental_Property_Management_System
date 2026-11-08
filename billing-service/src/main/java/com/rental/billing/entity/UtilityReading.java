package com.rental.billing.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(
    name = "utility_readings",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_reading_room_period_type",
        columnNames = {"room_id", "billing_period", "utility_type"}
    )
)
public class UtilityReading {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "billing_period", nullable = false,
            columnDefinition = "CHAR(7)")
    private String billingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "utility_type", nullable = false)
    private UtilityType utilityType;

    @Column(name = "previous_reading", nullable = false)
    private Long previousReading;

    @Column(name = "current_reading", nullable = false)
    private Long currentReading;

    @Column(name = "meter_replaced", nullable = false)
    private boolean meterReplaced;

    @Column(name = "meter_replacement_reason", length = 500)
    private String meterReplacementReason;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Column(name = "recorded_by", nullable = false, length = 36)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UtilityReading() {
    }

    public UtilityReading(
            String propertyId,
            String roomId,
            String billingPeriod,
            UtilityType utilityType,
            Long previousReading,
            Long currentReading,
            boolean meterReplaced,
            String meterReplacementReason,
            String evidenceUrl,
            String recordedBy) {

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.roomId = roomId;
        this.billingPeriod = billingPeriod;
        this.utilityType = utilityType;
        this.previousReading = previousReading;
        this.currentReading = currentReading;
        this.meterReplaced = meterReplaced;
        this.meterReplacementReason = meterReplacementReason;
        this.evidenceUrl = evidenceUrl;
        this.recordedBy = recordedBy;
    }

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        recordedAt = now;
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

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public UtilityType getUtilityType() {
        return utilityType;
    }

    public Long getPreviousReading() {
        return previousReading;
    }

    public Long getCurrentReading() {
        return currentReading;
    }

    public boolean isMeterReplaced() {
        return meterReplaced;
    }

    public String getMeterReplacementReason() {
        return meterReplacementReason;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}