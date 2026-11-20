package com.swiftdrop.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * Wrapper for every event on {@link EventTopology#EXCHANGE}. {@code eventType} is also the routing key.
 * {@code occurredAt} is an {@link Instant} written as an ISO-8601 UTC string whatever the ObjectMapper settings. Payloads must never contain personal data.
 */
public record EventEnvelope<T>(UUID eventId, int schemaVersion, String eventType,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
                               String producer, String correlationId, T data) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static <T> EventEnvelope<T> of(String eventType, String producer, String correlationId, T data) {
        return new EventEnvelope<>(UUID.randomUUID(), CURRENT_SCHEMA_VERSION, eventType, Instant.now(),
                producer, correlationId, data);
    }
}
