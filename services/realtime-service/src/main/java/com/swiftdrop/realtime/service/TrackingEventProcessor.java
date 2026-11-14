package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.realtime.domain.TrackingRecord;
import com.swiftdrop.realtime.repository.TrackingViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TrackingEventProcessor {

    private final TrackingViewRepository trackingViewRepository;
    private final ObjectMapper objectMapper;

    public TrackingEventProcessor(TrackingViewRepository trackingViewRepository, ObjectMapper objectMapper) {
        this.trackingViewRepository = trackingViewRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TrackingUpdateNotice process(String payload) {
        JsonNode event = parseEvent(payload);
        UUID parcelId = requiredUuid(event, "parcelId");
        UUID eventId = event.hasNonNull("eventId")
                ? requiredUuid(event, "eventId")
                : UUID.nameUUIDFromBytes(writeJson(event).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        if (!trackingViewRepository.markEventProcessed(eventId)) {
            return new TrackingUpdateNotice(requiredText(event, "trackingNo"), optionalUuid(event, "courierId"));
        }

        String trackingNo = requiredText(event, "trackingNo");
        Instant occurredAt = readOccurredAt(event);
        if (event.hasNonNull("toStatus")) {
            return processStatus(event, trackingNo, occurredAt)
                    ? new TrackingUpdateNotice(trackingNo, optionalUuid(event, "courierId")) : null;
        } else if (event.hasNonNull("courierId")) {
            if (trackingViewRepository.findByTrackingNo(trackingNo).isEmpty()) {
                throw new IllegalStateException("Received parcel assignment before its tracking record exists");
            }
            return new TrackingUpdateNotice(trackingNo, requiredUuid(event, "courierId"));
        } else if (event.hasNonNull("origin")) {
            String timelineJson = writeJson(List.of(new TrackingTimelineEntry("CREATED", occurredAt)));
            if (!trackingViewRepository.createTrackingView(trackingNo, parcelId, optionalText(event, "pickupCity"),
                    optionalText(event, "dropoffCity"), timelineJson, occurredAt)) {
                return null;
            }
            return new TrackingUpdateNotice(trackingNo, null);
        } else {
            throw new IllegalArgumentException("Unsupported realtime event payload");
        }
    }

    private boolean processStatus(JsonNode event, String trackingNo, Instant occurredAt) {
        String status = requiredText(event, "toStatus");
        TrackingRecord current = trackingViewRepository.findByTrackingNo(trackingNo)
                .orElseThrow(() -> new IllegalStateException("Received status event before its tracking record exists"));
        Long version = event.hasNonNull("aggregateVersion") ? event.get("aggregateVersion").asLong() : null;
        if (version != null && version <= current.aggregateVersion()) {
            return false;
        }
        String timelineEntry = writeJson(List.of(new TrackingTimelineEntry(status, occurredAt)));
        return trackingViewRepository.updateStatus(trackingNo, status, timelineEntry, occurredAt, version);
    }

    private JsonNode parseEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.has("data")) {
                return root;
            }
            if (!root.get("data").isObject()) {
                throw new IllegalArgumentException("Realtime event data must be an object");
            }
            ObjectNode data = (ObjectNode) root.get("data").deepCopy();
            if (root.hasNonNull("eventId")) {
                data.set("eventId", root.get("eventId"));
            }
            return data;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Realtime event payload is not valid JSON", exception);
        }
    }

    private Instant readOccurredAt(JsonNode event) {
        JsonNode occurredAt = event.get("occurredAt");
        if (occurredAt == null || occurredAt.isNull() || occurredAt.asText().isBlank()) {
            return Instant.now();
        }
        return Instant.parse(occurredAt.asText());
    }

    private UUID requiredUuid(JsonNode event, String field) {
        String value = requiredText(event, field);
        return UUID.fromString(value);
    }

    private UUID optionalUuid(JsonNode event, String field) {
        String value = optionalText(event, field);
        return value == null ? null : UUID.fromString(value);
    }

    private String requiredText(JsonNode event, String field) {
        String value = optionalText(event, field);
        if (value == null) {
            throw new IllegalArgumentException("Realtime event is missing " + field);
        }
        return value;
    }

    private String optionalText(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize realtime tracking event", exception);
        }
    }
}
