package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import java.math.BigDecimal;
import java.util.UUID;

public record QuoteRequest(
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        PricingStrategyType strategy,
        UUID pickupZoneId,
        UUID dropoffZoneId
) {
    public QuoteRequest {
        if (strategy == null) {
            strategy = PricingStrategyType.WEIGHT_TIERED;
        }
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            weightKg = BigDecimal.ONE;
        }
    }
}
