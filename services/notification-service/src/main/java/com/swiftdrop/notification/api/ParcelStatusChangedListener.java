package com.swiftdrop.notification.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.notification.service.Notifier;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Consumes parcel.status.changed and notifies the recipient and customer. Accepts both the flat payload parcel-service
 * publishes today and a full EventEnvelope ({"eventId", ..., "data": {...}}). An unreadable event goes to
 * notification.q.dlq instead of being retried for ever.
 */
@Component
public class ParcelStatusChangedListener {

    private final Notifier notifier;
    private final ObjectMapper json;

    public ParcelStatusChangedListener(Notifier notifier, ObjectMapper json) {
        this.notifier = notifier;
        this.json = json;
    }

    @RabbitListener(queues = EventTopology.NOTIFICATION_QUEUE)
    public void handle(String payload) {
        JsonNode event;
        try {
            event = json.readTree(payload);
            if (event.isTextual()) {
                event = json.readTree(event.asText()); // a JSON string holding the event
            }
        } catch (IOException e) {
            throw new AmqpRejectAndDontRequeueException("parcel.status.changed is not valid JSON", e);
        }
        JsonNode data = event.has("data") && event.get("data").isObject() ? event.get("data") : event;
        String parcelId = data.path("parcelId").asText("");
        String toStatus = data.path("toStatus").asText("");
        if (parcelId.isBlank() || toStatus.isBlank()) {
            throw new AmqpRejectAndDontRequeueException("parcel.status.changed is missing parcelId or toStatus");
        }
        String eventId = event.path("eventId").asText(data.path("eventId").asText(""));
        notifier.statusChanged(eventId.isBlank() ? null : UUID.fromString(eventId), UUID.fromString(parcelId), toStatus);
    }
}
