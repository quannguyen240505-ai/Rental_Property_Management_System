package com.rental.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_recipient_created", columnList = "recipient_user_id,created_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_notifications_source_event", columnNames = "source_event_id"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_event_id", updatable = false)
    private UUID sourceEventId; // Nullable for legacy/internal notifications; unique for RabbitMQ.

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() { }

    public Notification(UUID recipientUserId, String title, String message, String category) {
        this(null, recipientUserId, title, message, category);
    }

    public Notification(UUID sourceEventId, UUID recipientUserId, String title, String message, String category) {
        this.sourceEventId = sourceEventId;
        this.recipientUserId = recipientUserId;
        this.title = title;
        this.message = message;
        this.category = category;
    }

    public UUID getId() { return id; }
    public UUID getSourceEventId() { return sourceEventId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }
}
