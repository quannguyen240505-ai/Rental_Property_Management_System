package com.rental.notification.messaging;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * v1 delivery event. One eventId describes ONE notification for ONE recipient.
 * Producers must publish to the private broker with authorized credentials.
 */
public record NotificationEvent(
        int schemaVersion,
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID recipientUserId,
        UUID entityId,
        Payload payload) {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "CONTRACT_ACTIVATED", "VIEWING_ASSIGNED", "INVOICE_ISSUED",
            "PAYMENT_SUCCEEDED", "REQUEST_RESOLVED", "SYSTEM_NOTICE");

    public record Payload(String title, String message) { }

    /** Invalid messages go to the DLQ; never use arbitrary values as database categories. */
    public void validate() {
        if (schemaVersion != 1 || eventId == null || recipientUserId == null
                || occurredAt == null || !ALLOWED_TYPES.contains(eventType)
                || payload == null || payload.title() == null || payload.title().isBlank()
                || payload.message() == null || payload.message().isBlank()
                || payload.title().trim().length() > 160
                || payload.message().trim().length() > 1000) {
            throw new IllegalArgumentException("Invalid notification event v1");
        }
    }

    public String category() {
        return switch (eventType) {
            case "CONTRACT_ACTIVATED", "REQUEST_RESOLVED" -> "RENTAL";
            case "VIEWING_ASSIGNED" -> "PROPERTY";
            case "INVOICE_ISSUED" -> "BILLING";
            case "PAYMENT_SUCCEEDED" -> "PAYMENT";
            case "SYSTEM_NOTICE" -> "SYSTEM";
            default -> throw new IllegalArgumentException("Unsupported notification event type");
        };
    }
}
