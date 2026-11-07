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
        UUID merchantId
) {
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
    }
}
