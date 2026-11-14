package com.swiftdrop.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record WebhookEndpointRequest(
        @NotBlank(message = "Webhook URL is required")
        @URL(message = "Must be a valid URL")
        String url
) {
}

