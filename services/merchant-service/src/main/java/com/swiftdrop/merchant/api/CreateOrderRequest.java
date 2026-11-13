package com.swiftdrop.merchant.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String orderRef,
        @NotBlank @Size(max = 500) String pickupAddress,
        @NotBlank @Size(max = 100) String pickupCity,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal pickupLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal pickupLng,
        @NotBlank @Size(max = 500) String dropoffAddress,
        @NotBlank @Size(max = 100) String dropoffCity,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal dropoffLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal dropoffLng,
        @NotBlank @Size(max = 150) String recipientName,
        @NotBlank @Pattern(regexp = "(?:0|[+]94)7[0-9]{8}") String recipientPhone,
        @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal weightKg,
        @DecimalMin("0.01") @DecimalMax("9999") BigDecimal lengthCm,
        @DecimalMin("0.01") @DecimalMax("9999") BigDecimal widthCm,
        @DecimalMin("0.01") @DecimalMax("9999") BigDecimal heightCm,
        @Pattern(regexp = "SMALL|MEDIUM|LARGE") String size,
        @Pattern(regexp = "STANDARD|EXPRESS") String serviceType,
        UUID pickupZoneId,
        UUID dropoffZoneId) {

    public CreateOrderRequest {
        if (size == null) size = "MEDIUM";
        if (serviceType == null) serviceType = "STANDARD";
    }

    @AssertTrue(message = "Provide all three dimensions or leave all three empty")
    public boolean isDimensionsComplete() {
        int count =
                (lengthCm != null ? 1 : 0) + (widthCm != null ? 1 : 0) + (heightCm != null ? 1 : 0);
        return count == 0 || count == 3;
    }
}
