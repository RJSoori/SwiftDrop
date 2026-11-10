package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateParcelRequest(
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
        UUID merchantId,
        String pickupCity,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String dropoffCity,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,
        String recipientName,
        String recipientPhone,
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
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            weightKg = BigDecimal.ONE;
        }
        if (serviceType == null || serviceType.isBlank()) {
            serviceType = "STANDARD";
        }
    }
}
