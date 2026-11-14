package com.swiftdrop.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.reporting.domain.StatsCounter;
import com.swiftdrop.reporting.repository.DailyStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds daily_stats from parcel events. parcel.created counts a booking; parcel.status.changed to DELIVERED or
 * FAILED counts a delivery outcome (a re-attempt that fails again counts again). The eventId guard and the counter
 * update share one transaction, so a redelivered event is never counted twice and a failed update is retried.
 */
@Component
public class ReportingEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReportingEventListener.class);

    private final ObjectMapper json;
    private final DailyStatsRepository stats;

    public ReportingEventListener(ObjectMapper json, DailyStatsRepository stats) {
        this.json = json;
        this.stats = stats;
    }

    @RabbitListener(queues = EventTopology.REPORTING_QUEUE)
    @Transactional
    public void receive(Message message) {
        JsonNode envelope = read(message);
        UUID eventId = uuid(envelope.path("eventId").asText(null))
                .orElseThrow(() -> reject("event without a valid eventId"));
        String eventType = envelope.path("eventType")
                .asText(message.getMessageProperties().getReceivedRoutingKey());
        JsonNode data = envelope.path("data");

        Optional<StatsCounter> counter = counterFor(eventType, data);
        if (counter.isEmpty()) {
            return; // another status (PICKED_UP, ...) or event type: nothing to count
        }
        LocalDate day = businessDay(data, envelope);
        if (!stats.markProcessed(eventId, eventType)) {
            log.debug("Skipping duplicate event {}", eventId);
            return;
        }
        stats.increment(day, counter.get());
    }

    static Optional<StatsCounter> counterFor(String eventType, JsonNode data) {
        if (EventTopology.PARCEL_CREATED.equals(eventType)) {
            return Optional.of(StatsCounter.CREATED);
        }
        if (EventTopology.PARCEL_STATUS_CHANGED.equals(eventType)) {
            return switch (data.path("toStatus").asText("")) {
                case "DELIVERED" -> Optional.of(StatsCounter.DELIVERED);
                case "FAILED" -> Optional.of(StatsCounter.FAILED);
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    /** The Sri Lankan day the business event happened: data.occurredAt when present, else the envelope's. */
    private static LocalDate businessDay(JsonNode data, JsonNode envelope) {
        Instant at = instant(data.path("occurredAt").asText(null))
                .or(() -> instant(envelope.path("occurredAt").asText(null)))
                .orElseThrow(() -> reject("event without a valid occurredAt"));
        return LocalDate.ofInstant(at, ReportService.BUSINESS_ZONE);
    }

    private JsonNode read(Message message) {
        try {
            JsonNode node = json.readTree(message.getBody());
            if (node == null || !node.isObject()) {
                throw reject("event body is not a JSON object");
            }
            return node;
        } catch (IOException e) {
            throw reject("event body is not JSON");
        }
    }

    private static Optional<UUID> uuid(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> instant(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Sends the message to reporting.q.dlq: retrying a malformed event can never succeed. */
    private static AmqpRejectAndDontRequeueException reject(String reason) {
        log.warn("Dead-lettering reporting event: {}", reason);
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
