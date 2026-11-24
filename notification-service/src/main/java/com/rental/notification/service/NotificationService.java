package com.rental.notification.service;

import com.rental.notification.dto.NotificationResponse;
import com.rental.notification.entity.Notification;
import com.rental.notification.messaging.NotificationEvent;
import com.rental.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> myNotifications(UUID userId) {
        return notifications.findAllByRecipientUserIdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        // Owner-scoped lookup: never return another user's notification.
        Notification n = notifications.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.markRead();
        return NotificationResponse.from(n);
    }

    /**
     * At-least-once delivery with idempotency. The unique DB constraint protects
     * even if several app instances race; a race loser is retried and then skips.
     */
    @Transactional
    public void deliverEvent(NotificationEvent event) {
        event.validate();
        if (notifications.existsBySourceEventId(event.eventId())) {
            return;
        }
        var n = new Notification(event.eventId(), event.recipientUserId(),
                event.payload().title().trim(), event.payload().message().trim(), event.category());
        notifications.saveAndFlush(n);
    }

    /** Internal Java API, not exposed as a public REST endpoint. */
    @Transactional
    public NotificationResponse deliver(UUID recipientId, String title, String message, String category) {
        if (recipientId == null || title == null || title.isBlank() || title.length() > 160
                || message == null || message.isBlank() || message.length() > 1000
                || category == null || !List.of("SYSTEM", "PROPERTY", "RENTAL", "BILLING", "PAYMENT")
                        .contains(category)) {
            throw new IllegalArgumentException("Invalid notification");
        }
        Notification saved = notifications.save(new Notification(recipientId, title.trim(), message.trim(), category));
        return NotificationResponse.from(saved);
    }
}
