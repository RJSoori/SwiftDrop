package com.swiftdrop.merchant.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateMerchantOrderRequest(
        @Size(max = 100)
        String orderRef,

        @NotBlank(message = "Pickup address is required")
        String pickupAddress,

        @NotBlank(message = "Pickup city is required")
        String pickupCity,

        BigDecimal pickupLat,
        BigDecimal pickupLng,

        @NotBlank(message = "Dropoff address is required")
        String dropoffAddress,

        @NotBlank(message = "Dropoff city is required")
        String dropoffCity,

        BigDecimal dropoffLat,
        BigDecimal dropoffLng,

        @NotBlank(message = "Recipient name is required")
        String recipientName,

        @NotBlank(message = "Recipient phone is required")
        String recipientPhone,

        @NotNull(message = "Weight is required")
        @Positive(message = "Weight must be positive")
        BigDecimal weightKg,

        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,

        String size,
        String serviceType
) {
}

