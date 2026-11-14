package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Customer booking. Bean Validation runs on the controller's @Valid, so a bad body is a 400 problem. */
public record CreateParcelRequest(
        @NotBlank String pickupAddress,
        @NotBlank String dropoffAddress,
        @NotNull @Positive @DecimalMax("100") BigDecimal weightKg,
        @Positive BigDecimal lengthCm,
        @Positive BigDecimal widthCm,
        @Positive BigDecimal heightCm,
        String size,
        PricingStrategyType strategy,
        UUID pickupZoneId,
        UUID dropoffZoneId,
        UUID merchantId,
        String pickupCity,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String dropoffCity,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        String serviceType
) {
    public CreateParcelRequest(
            String pickupAddress,
            String dropoffAddress,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            String size,
            PricingStrategyType strategy,
            UUID pickupZoneId,
            UUID dropoffZoneId,
            UUID merchantId
    ) {
        this(pickupAddress, dropoffAddress, weightKg, lengthCm, widthCm, heightCm, size, strategy,
                pickupZoneId, dropoffZoneId, merchantId, null, null, null, null, null, null, null, null, "STANDARD");
    }

    public CreateParcelRequest {
        if (size == null || size.isBlank()) {
            size = "MEDIUM";
        }
        if (strategy == null) {
            strategy = PricingStrategyType.WEIGHT_TIERED;
        }
        if (serviceType == null || serviceType.isBlank()) {
            serviceType = "STANDARD";
        }
    }
}
