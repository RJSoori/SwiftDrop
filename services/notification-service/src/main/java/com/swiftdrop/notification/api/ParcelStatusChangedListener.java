package com.swiftdrop.notification.api;

import org.springframework.stereotype.Component;

/**
 * Will consume {@code parcel.status.changed} events and send email/SMS notifications.
 * <p>
 * TODO(Phase 2): declare the exchange/queue binding in config/, then add
 * {@code @RabbitListener(queues = "parcel.status.changed")} on a handler method here.
 * The RabbitMQ connection itself is already checked by /actuator/health (spring-boot-starter-amqp).
 */
@Component
public class ParcelStatusChangedListener {
}
