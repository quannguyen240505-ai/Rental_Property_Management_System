package com.rental.notification.repository;

import com.rental.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);
    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);
    boolean existsBySourceEventId(UUID sourceEventId);
}
