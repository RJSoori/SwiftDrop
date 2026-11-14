package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.outbox.OutboxEvent;
import com.swiftdrop.common.outbox.OutboxEventRepository;
import com.swiftdrop.common.outbox.OutboxRelay;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Parcel-side adapter until the shared event-bus owner replaces the base relay. */
@Component
@ConditionalOnProperty(prefix = "swiftdrop.outbox", name = "enabled", havingValue = "true")
public class ParcelOutboxRelay extends OutboxRelay {
    private final ObjectMapper json;

    public ParcelOutboxRelay(
            OutboxEventRepository events,
            ObjectProvider<RabbitTemplate> rabbit,
            ObjectMapper json) {
        super(events, rabbit);
        this.json = json;
    }

    @Override
    protected void publish(RabbitTemplate rabbit, OutboxEvent event) {
        try {
            if (event.getId() == null)
                throw new IllegalStateException("Outbox event must be persisted before publishing");
            var envelope =
                    new EventEnvelope<>(
                            event.getId(),
                            1,
                            event.getEventType(),
                            event.getCreatedAt(),
                            "parcel-service",
                            event.getId().toString(),
                            json.readTree(event.getPayload()));
            var props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("UTF-8");
            props.setMessageId(event.getId().toString());
            props.setType(event.getEventType());
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            var confirm = new CorrelationData(UUID.randomUUID().toString());
            rabbit.setMandatory(true);
            rabbit.send(
                    EventTopology.EXCHANGE,
                    event.getEventType(),
                    new Message(json.writeValueAsBytes(envelope), props),
                    confirm);
            if (!confirm.getFuture().get(5, TimeUnit.SECONDS).isAck()
                    || confirm.getReturned() != null)
                throw new IllegalStateException("Parcel event was not routed and confirmed");
        } catch (Exception e) {
            throw new IllegalStateException("Parcel event publish failed", e);
        }
    }
}
