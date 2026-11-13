package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import java.math.BigDecimal;
import java.util.UUID;

public record InternalCreateParcelRequest(
        UUID merchantId,
        String merchantOrderRef,
        String pickupAddress,
        String pickupCity,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String dropoffAddress,
        String dropoffCity,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,
        String recipientName,
        String recipientPhone,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String size,
        PricingStrategyType strategy,
        UUID pickupZoneId,
        UUID dropoffZoneId,
        String serviceType
) {
    public InternalCreateParcelRequest {
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
