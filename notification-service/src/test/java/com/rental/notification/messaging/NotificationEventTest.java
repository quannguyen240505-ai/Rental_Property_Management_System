package com.rental.notification.messaging;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEventTest {
    private NotificationEvent valid(String type) {
        return new NotificationEvent(1, UUID.randomUUID(), type, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(),
                new NotificationEvent.Payload("  Hóa đơn  ", "  Có hóa đơn mới  "));
    }

    @Test
    void acceptsV1AndMapsEventTypeToCategory() {
        assertDoesNotThrow(() -> valid("INVOICE_ISSUED").validate());
        assertEquals("BILLING", valid("INVOICE_ISSUED").category());
        assertEquals("RENTAL", valid("CONTRACT_ACTIVATED").category());
        assertEquals("PROPERTY", valid("VIEWING_ASSIGNED").category());
        assertEquals("PAYMENT", valid("PAYMENT_SUCCEEDED").category());
        assertEquals("SYSTEM", valid("SYSTEM_NOTICE").category());
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        var e = valid("SYSTEM_NOTICE");
        var wrong = new NotificationEvent(2, e.eventId(), e.eventType(), e.occurredAt(),
                e.recipientUserId(), e.entityId(), e.payload());
        assertThrows(IllegalArgumentException.class, wrong::validate);
    }

    @Test
    void rejectsUnknownEventTypeOrMissingRecipient() {
        assertThrows(IllegalArgumentException.class, () -> valid("OTHER").validate());
        var e = valid("SYSTEM_NOTICE");
        var missingRecipient = new NotificationEvent(1, e.eventId(), e.eventType(),
                e.occurredAt(), null, e.entityId(), e.payload());
        assertThrows(IllegalArgumentException.class, missingRecipient::validate);
    }

    @Test
    void rejectsBlankOrOversizedMessages() {
        var e = valid("SYSTEM_NOTICE");
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(1,
                e.eventId(), e.eventType(), e.occurredAt(), e.recipientUserId(), e.entityId(),
                new NotificationEvent.Payload(" ", "text")).validate());
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(1,
                e.eventId(), e.eventType(), e.occurredAt(), e.recipientUserId(), e.entityId(),
                new NotificationEvent.Payload("title", "a".repeat(1001))).validate());
    }
}
