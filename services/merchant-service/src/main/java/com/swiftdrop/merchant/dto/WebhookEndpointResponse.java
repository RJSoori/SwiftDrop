package com.swiftdrop.merchant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.merchant.domain.WebhookEndpoint;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookEndpointResponse(
        UUID id,
        UUID merchantId,
        String url,
        String secret,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static WebhookEndpointResponse of(WebhookEndpoint endpoint, String plaintextSecret) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getMerchantId(),
                endpoint.getUrl(),
                plaintextSecret,
                endpoint.isActive(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}

