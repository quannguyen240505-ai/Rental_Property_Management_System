package com.rental.notification.dto;

import com.rental.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String title, String message, String category, boolean read,
		Instant createdAt, Instant readAt) {
	public static NotificationResponse from(Notification n) {
		return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.getCategory(), n.getReadAt() != null,
				n.getCreatedAt(), n.getReadAt());
	}
}
