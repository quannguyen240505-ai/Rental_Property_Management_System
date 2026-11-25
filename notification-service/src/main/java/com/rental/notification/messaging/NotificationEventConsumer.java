package com.rental.notification.messaging;

import com.rental.notification.service.NotificationService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ acknowledges only after the transactional database write succeeds.
 * Invalid payloads are rejected (DLQ); transient database errors are retried
 * by the listener container and then dead-lettered after exhaustion.
 */
@Component
public class NotificationEventConsumer {
    private static final int MAX_EVENT_BYTES = 16 * 1024;
    private final JsonMapper jsonMapper;
    private final NotificationService notifications;

    public NotificationEventConsumer(JsonMapper jsonMapper, NotificationService notifications) {
        this.jsonMapper = jsonMapper;
        this.notifications = notifications;
    }

    @RabbitListener(queues = "${app.notification.rabbit.queue}")
    public void consume(Message message) {
        final NotificationEvent event;
        try {
            String contentType = message.getMessageProperties().getContentType();
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("application/json") || message.getBody().length > MAX_EVENT_BYTES) {
                throw new IllegalArgumentException("Expected small application/json event");
            }
            event = jsonMapper.readValue(message.getBody(), NotificationEvent.class);
            if (event == null) {
                throw new IllegalArgumentException("Empty notification event");
            }
            event.validate();
        } catch (Exception invalid) {
            // Schema errors cannot be fixed by retrying. Keep them in DLQ for inspection.
            throw new AmqpRejectAndDontRequeueException("Invalid notification message", invalid);
        }
        // Database exceptions are deliberately allowed to propagate for bounded retry.
        notifications.deliverEvent(event);
    }
}
