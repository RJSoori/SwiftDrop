package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.ParcelStatusChanged;
import com.swiftdrop.merchant.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class WebhookEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventListener.class);

    private final MerchantOrderService merchantOrderService;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public WebhookEventListener(MerchantOrderService merchantOrderService,
                                WebhookService webhookService,
                                ObjectMapper objectMapper) {
        this.merchantOrderService = merchantOrderService;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.WEBHOOK_QUEUE)
    public void handleWebhookMessage(Message message) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);

            // Check if this is a retry message from webhook.retry.q
            if (root.has("attempt") && root.has("merchantId") && root.has("eventId")) {
                WebhookService.RetryPayload retry = objectMapper.readValue(json, WebhookService.RetryPayload.class);
                log.info("Processing retry delivery attempt {} for merchant {}", retry.attempt(), retry.merchantId());
                webhookService.dispatchWebhook(retry.merchantId(), retry.eventId(), retry.body(), retry.attempt());
                return;
            }

            // Otherwise, it is an EventEnvelope from parcel.status.changed
            if (root.has("eventType") && root.has("data")) {
                EventEnvelope<ParcelStatusChanged> envelope = objectMapper.readValue(
                        json,
                        new TypeReference<EventEnvelope<ParcelStatusChanged>>() {}
                );

                ParcelStatusChanged event = envelope.data();
                if (event == null || event.merchantId() == null) {
                    log.debug("Skipping event {}: no merchant associated", envelope.eventId());
                    return;
                }

                log.info("Received parcel.status.changed event {} for merchant {}: status changed to {}",
                        envelope.eventId(), event.merchantId(), event.toStatus());

                // Update local order status
                merchantOrderService.updateOrderStatus(event.parcelId(), event.toStatus());

                // Dispatch webhook to merchant
                webhookService.dispatchWebhook(event.merchantId(), envelope.eventId(), event, 1);
            }
        } catch (Exception e) {
            log.error("Failed to process message from webhook queue: {}", e.getMessage(), e);
        }
    }
}

