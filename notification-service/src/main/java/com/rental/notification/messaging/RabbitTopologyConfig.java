package com.rental.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Durable main queue + a non-cycling dead-letter queue. */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
public class RabbitTopologyConfig {
    @Bean
    TopicExchange notificationEventsExchange(@Value("${app.notification.rabbit.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    TopicExchange notificationDeadLetterExchange(@Value("${app.notification.rabbit.dlx}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue notificationQueue(@Value("${app.notification.rabbit.queue}") String queue,
            @Value("${app.notification.rabbit.dlx}") String dlx,
            @Value("${app.notification.rabbit.dead-routing-key}") String deadRoutingKey) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue(@Value("${app.notification.rabbit.dead-queue}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    Binding notificationBinding(@Qualifier("notificationQueue") Queue queue,
            @Qualifier("notificationEventsExchange") TopicExchange exchange,
            @Value("${app.notification.rabbit.routing-key}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    @Bean
    Binding notificationDeadLetterBinding(@Qualifier("notificationDeadLetterQueue") Queue queue,
            @Qualifier("notificationDeadLetterExchange") TopicExchange exchange,
            @Value("${app.notification.rabbit.dead-routing-key}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
