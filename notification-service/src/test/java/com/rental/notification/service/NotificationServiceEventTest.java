package com.rental.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.rental.notification.entity.Notification;
import com.rental.notification.messaging.NotificationEvent;
import com.rental.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceEventTest {
    private NotificationEvent event() {
        return new NotificationEvent(1, UUID.randomUUID(), "INVOICE_ISSUED", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(),
                new NotificationEvent.Payload("  Hóa đơn mới ", "  Vui lòng xem hóa đơn  "));
    }

    @Test
    void writesEventIdAndRecipientWhenNewEventArrives() {
        NotificationRepository repo = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repo);
        NotificationEvent e = event();
        service.deliverEvent(e);
        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(e.eventId(), saved.getSourceEventId());
        assertEquals(e.recipientUserId(), saved.getRecipientUserId());
        assertEquals("BILLING", saved.getCategory());
        assertEquals("Hóa đơn mới", saved.getTitle());
    }

    @Test
    void ignoresRedeliveryWithSameEventId() {
        NotificationRepository repo = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repo);
        NotificationEvent e = event();
        when(repo.existsBySourceEventId(e.eventId())).thenReturn(true);
        service.deliverEvent(e);
        verify(repo, never()).saveAndFlush(any(Notification.class));
    }
}
