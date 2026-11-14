package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.dispatch.domain.ParcelQueueEntry;
import com.swiftdrop.dispatch.domain.ProcessedEvent;
import com.swiftdrop.dispatch.repository.ParcelQueueRepository;
import com.swiftdrop.dispatch.repository.ProcessedEventRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
public class ParcelEventListener {
    private final ObjectMapper objectMapper;
    private final ParcelQueueRepository parcelQueueRepository;
    private final ProcessedEventRepository processedEventRepository;

    public ParcelEventListener(ObjectMapper objectMapper, ParcelQueueRepository parcelQueueRepository,
                               ProcessedEventRepository processedEventRepository) {
        this.objectMapper = objectMapper;
        this.parcelQueueRepository = parcelQueueRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = EventTopology.DISPATCH_QUEUE)
    @Transactional
    public void receive(Message message) {
        try {
            JsonNode envelope = objectMapper.readTree(message.getBody());
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());
            if (processedEventRepository.existsById(eventId)) return;
            String eventType = envelope.path("eventType").asText(message.getMessageProperties().getReceivedRoutingKey());
            JsonNode data = envelope.path("data");
            Instant occurredAt = Instant.parse(envelope.path("occurredAt").asText());

            if (EventTopology.PARCEL_CREATED.equals(eventType)) {
                UUID parcelId = UUID.fromString(data.path("parcelId").asText());
                parcelQueueRepository.findById(parcelId).ifPresentOrElse(existing -> {
                    existing.updateStatus("CREATED", occurredAt);
                }, () -> parcelQueueRepository.save(new ParcelQueueEntry(parcelId,
                        data.path("trackingNo").asText(), data.path("pickupCity").asText(),
                        data.path("dropoffCity").asText(), uuidOrNull(data, "pickupZoneId"),
                        data.path("weightKg").decimalValue(), "CREATED", occurredAt)));
            } else if (EventTopology.PARCEL_STATUS_CHANGED.equals(eventType)) {
                UUID parcelId = UUID.fromString(data.path("parcelId").asText());
                parcelQueueRepository.findById(parcelId).ifPresent(entry ->
                        entry.updateStatus(data.path("toStatus").asText(), occurredAt));
            } else {
                return;
            }
            processedEventRepository.save(new ProcessedEvent(eventId));
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Invalid dispatch event", ex);
        }
    }

    private static UUID uuidOrNull(JsonNode node, String name) {
        String value = node.path(name).asText(null);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
