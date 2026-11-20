package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Quote result. The zone and rate fields explain the fee and are null when the default rates were used.
 */
public record QuoteResponse(
        BigDecimal feeLkr,
        BigDecimal actualWeightKg,
        BigDecimal volumetricWeightKg,
        BigDecimal chargeableWeightKg,
        PricingStrategyType strategy,
        String currency,
        UUID pickupZoneId,
        String pickupZoneName,
        UUID dropoffZoneId,
        String dropoffZoneName,
        BigDecimal baseFeeLkr,
        BigDecimal perKgFeeLkr,
        BigDecimal sizeMultiplier
) {
    public static QuoteResponse of(BigDecimal feeLkr, BigDecimal actualWeight,
                                  BigDecimal volWeight, BigDecimal chargeableWeight,
                                  PricingStrategyType strategy) {
        return new QuoteResponse(feeLkr, actualWeight, volWeight, chargeableWeight, strategy, "LKR",
                null, null, null, null, null, null, null);
    }
}
