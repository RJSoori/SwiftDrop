package com.swiftdrop.merchant.dto;

import com.swiftdrop.merchant.domain.WebhookDelivery;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        UUID merchantId,
        UUID eventId,
        int attempt,
        String state,
        Integer statusCode,
        String lastError,
        Instant nextRetryAt,
        Instant createdAt,
        Instant deliveredAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery d) {
        return new WebhookDeliveryResponse(
                d.getId(),
                d.getMerchantId(),
                d.getEventId(),
                d.getAttempt(),
                d.getState(),
                d.getStatusCode(),
                d.getLastError(),
                d.getNextRetryAt(),
                d.getCreatedAt(),
                d.getDeliveredAt()
        );
    }
}

