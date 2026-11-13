package com.swiftdrop.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Publishes events to {@link EventTopology#EXCHANGE} the way the outbox relay does in production: the full
 * {@link EventEnvelope} as JSON, routing key = event type, {@code messageId} = eventId, persistent.
 * Registered as a bean by {@link RabbitContainerConfig}; inject it with {@code @Autowired TestEvents events}.
 *
 * <pre>{@code
 * EventEnvelope<ParcelStatusChanged> sent = events.publish(EventTopology.PARCEL_STATUS_CHANGED, payload);
 * events.publish(sent); // the same eventId again, to test that your consumer skips duplicates
 * }</pre>
 */
public class TestEvents {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private boolean exchangeDeclared;

    public TestEvents(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /** Wraps {@code data} in a new envelope (fresh eventId) and publishes it. Returns the envelope sent. */
    public <T> EventEnvelope<T> publish(String eventType, T data) {
        EventEnvelope<T> envelope = EventEnvelope.of(eventType, "test", UUID.randomUUID().toString(), data);
        publish(envelope);
        return envelope;
    }

    /** Publishes an envelope exactly as given. Call it twice with the same envelope to simulate a redelivery. */
    public void publish(EventEnvelope<?> envelope) {
        declareExchangeOnce();
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setMessageId(envelope.eventId().toString());
        props.setType(envelope.eventType());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbitTemplate.send(EventTopology.EXCHANGE, envelope.eventType(), new Message(toJson(envelope), props));
    }

    /** Consumers declare their own queues; the exchange may not exist yet in a fresh test broker. */
    private synchronized void declareExchangeOnce() {
        if (!exchangeDeclared) {
            new RabbitAdmin(rabbitTemplate).declareExchange(new TopicExchange(EventTopology.EXCHANGE, true, false));
            exchangeDeclared = true;
        }
    }

    private byte[] toJson(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event payload cannot be written as JSON: " + envelope.eventType(), e);
        }
    }
}
