package com.rental.notification.controller;

import com.rental.notification.dto.NotificationResponse;
import com.rental.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping("/me")
	public List<NotificationResponse> mine(Authentication authentication) {
		return notifications.myNotifications(UUID.fromString(authentication.getName()));
	}

	@PatchMapping("/{id}/read")
	public NotificationResponse read(@PathVariable UUID id, Authentication authentication) {
		return notifications.markRead(UUID.fromString(authentication.getName()), id);
	}
}
