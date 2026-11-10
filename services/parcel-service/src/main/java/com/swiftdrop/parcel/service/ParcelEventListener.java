package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RabbitMQ Listener consuming parcel events.
 * Listens to queue 'parcel.q' for 'parcel.assigned' to set courier_id.
 */
@Component
public class ParcelEventListener {

    private static final Logger log = LoggerFactory.getLogger(ParcelEventListener.class);

    private final ParcelService parcelService;
    private final ObjectMapper objectMapper;

    public ParcelEventListener(ParcelService parcelService, ObjectMapper objectMapper) {
        this.parcelService = parcelService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.PARCEL_QUEUE_NAME)
    public void handleParcelAssigned(String messagePayload) {
        try {
            log.info("Received message on parcel.q: {}", messagePayload);
            JsonNode root = objectMapper.readTree(messagePayload);

            // Handle both enveloped (root.get("data")) and flat payloads
            JsonNode dataNode = root.has("data") ? root.get("data") : root;

            if (dataNode.has("parcelId") && dataNode.has("courierId")) {
                String parcelIdStr = dataNode.get("parcelId").asText();
                String courierIdStr = dataNode.get("courierId").asText();

                if (!parcelIdStr.isBlank() && !courierIdStr.isBlank()) {
                    UUID parcelId = UUID.fromString(parcelIdStr);
                    UUID courierId = UUID.fromString(courierIdStr);
                    parcelService.setAssignedCourier(parcelId, courierId);
                    log.info("Successfully processed parcel.assigned: parcel={}, courier={}", parcelId, courierId);
                }
            } else {
                log.warn("Payload missing parcelId or courierId: {}", messagePayload);
            }
        } catch (Exception e) {
            log.error("Failed to process parcel.assigned event: {}", messagePayload, e);
        }
    }
}
